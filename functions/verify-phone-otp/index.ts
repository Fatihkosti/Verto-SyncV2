import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type'
};
function normalizeSudanesePhone(raw) {
  const digits = raw.replace(/\D/g, '');
  if (digits.startsWith('249')) return digits;
  if (digits.startsWith('0')) return '249' + digits.slice(1);
  if (digits.length === 9) return '249' + digits;
  return digits;
}
async function sha256Hex(value) {
  const hashBuffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return Array.from(new Uint8Array(hashBuffer)).map((b)=>b.toString(16).padStart(2, '0')).join('');
}
function clientIp(req) {
  return req.headers.get('cf-connecting-ip') ?? req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ?? 'unknown';
}
async function logOtpAttempt(supabase, phoneHash, ipHash, success, reason) {
  await supabase.from('phone_otp_attempt_log').insert({
    phone_hash: phoneHash,
    ip_hash: ipHash,
    action: 'verify',
    success,
    reason
  });
}
Deno.serve(async (req)=>{
  if (req.method === 'OPTIONS') {
    return new Response('ok', {
      headers: corsHeaders
    });
  }
  try {
    const { phone, otp } = await req.json();
    if (!phone || !otp) {
      return new Response(JSON.stringify({
        error: 'رقم الهاتف والرمز مطلوبان',
        code: 'invalid_request'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    const normalizedPhone = normalizeSudanesePhone(String(phone));
    const normalizedOtp = String(otp).trim();
    const supabase = createClient(Deno.env.get('SUPABASE_URL'), Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'));
    const phoneHash = await sha256Hex(normalizedPhone);
    const ipHash = await sha256Hex(clientIp(req));
    const tenMinutesAgo = new Date(Date.now() - 10 * 60_000).toISOString();
    const { count: recentFailures } = await supabase.from('phone_otp_attempt_log').select('id', {
      count: 'exact',
      head: true
    }).eq('action', 'verify').eq('success', false).gte('created_at', tenMinutesAgo).or(`phone_hash.eq.${phoneHash},ip_hash.eq.${ipHash}`);
    if ((recentFailures ?? 0) >= 10) {
      await logOtpAttempt(supabase, phoneHash, ipHash, false, 'rate_limited');
      return new Response(JSON.stringify({
        error: 'تعذّر التحقق من الرمز',
        code: 'otp_invalid_or_expired'
      }), {
        status: 429,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // تشفير الكود المُدخَل
    const providedHash = await sha256Hex(normalizedOtp);
    // جلب أحدث OTP صالح لهذا الرقم
    const { data: otpRecords, error: fetchError } = await supabase.from('phone_otps').select('*').eq('phone', normalizedPhone).eq('used', false).gte('expires_at', new Date().toISOString()).order('created_at', {
      ascending: false
    }).limit(1);
    if (fetchError || !otpRecords || otpRecords.length === 0) {
      await logOtpAttempt(supabase, phoneHash, ipHash, false, 'invalid_or_expired');
      return new Response(JSON.stringify({
        error: 'الرمز غير صالح أو انتهت صلاحيته',
        code: 'otp_invalid_or_expired'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    const otpRecord = otpRecords[0];
    if (otpRecord.attempts >= 5) {
      await supabase.from('phone_otps').update({
        used: true
      }).eq('id', otpRecord.id);
      await logOtpAttempt(supabase, phoneHash, ipHash, false, 'attempts_exceeded');
      return new Response(JSON.stringify({
        error: 'الرمز غير صالح أو انتهت صلاحيته',
        code: 'otp_attempts_exceeded'
      }), {
        status: 429,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    await supabase.from('phone_otps').update({
      attempts: otpRecord.attempts + 1
    }).eq('id', otpRecord.id);
    if (otpRecord.otp_hash !== providedHash) {
      await logOtpAttempt(supabase, phoneHash, ipHash, false, 'mismatch');
      return new Response(JSON.stringify({
        error: 'الرمز غير صالح أو انتهت صلاحيته',
        code: 'otp_mismatch'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    await supabase.from('phone_otps').update({
      used: true
    }).eq('id', otpRecord.id);
    await logOtpAttempt(supabase, phoneHash, ipHash, true, 'verified');
    // إيجاد أو إنشاء مستخدم Auth
    let userEmail;
    const { data: autodriveUser } = await supabase.from('autodrive_users').select('user_id').eq('phone', normalizedPhone).single();
    if (autodriveUser?.user_id) {
      const { data: authUserData, error: authErr } = await supabase.auth.admin.getUserById(autodriveUser.user_id);
      if (authErr || !authUserData?.user) {
        console.error('[verify-phone-otp] getUserById failed');
        return new Response(JSON.stringify({
          error: 'خطأ في جلب بيانات المستخدم',
          code: 'auth_lookup_failed'
        }), {
          status: 500,
          headers: {
            ...corsHeaders,
            'Content-Type': 'application/json'
          }
        });
      }
      userEmail = authUserData.user.email;
    } else {
      userEmail = `phone_${normalizedPhone}@phone.autodrive`;
    }
    // generateLink يُنشئ المستخدم إن لم يكن موجوداً ويُولّد token مؤقت
    const { data: linkData, error: linkErr } = await supabase.auth.admin.generateLink({
      type: 'magiclink',
      email: userEmail,
      options: {
        redirectTo: 'autodrive://auth'
      }
    });
    if (linkErr || !linkData?.properties?.hashed_token) {
      console.error('[verify-phone-otp] generateLink failed');
      return new Response(JSON.stringify({
        error: 'فشل إنشاء الجلسة — حاول مجدداً',
        code: 'session_create_failed'
      }), {
        status: 500,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // تبادل hashed_token بـ access_token + refresh_token
    const verifyResponse = await fetch(`${Deno.env.get('SUPABASE_URL')}/auth/v1/verify`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'apikey': Deno.env.get('SUPABASE_ANON_KEY')
      },
      body: JSON.stringify({
        type: 'magiclink',
        token_hash: linkData.properties.hashed_token
      })
    });
    if (!verifyResponse.ok) {
      console.error('[verify-phone-otp] token exchange failed:', verifyResponse.status);
      return new Response(JSON.stringify({
        error: 'فشل تبادل الرمز — حاول مجدداً',
        code: 'token_exchange_failed'
      }), {
        status: 500,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    const sessionData = await verifyResponse.json();
    return new Response(JSON.stringify({
      access_token: sessionData.access_token,
      refresh_token: sessionData.refresh_token,
      expires_in: sessionData.expires_in ?? 3600,
      token_type: sessionData.token_type ?? 'bearer',
      user_id: sessionData.user?.id ?? ''
    }), {
      status: 200,
      headers: {
        ...corsHeaders,
        'Content-Type': 'application/json'
      }
    });
  } catch (e) {
    console.error('[verify-phone-otp] Unexpected error');
    return new Response(JSON.stringify({
      error: 'خطأ غير متوقع',
      code: 'internal_error'
    }), {
      status: 500,
      headers: {
        ...corsHeaders,
        'Content-Type': 'application/json'
      }
    });
  }
});
