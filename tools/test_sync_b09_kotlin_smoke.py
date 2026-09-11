#!/usr/bin/env python3
"""Offline B09 Kotlin check: REAL mapping/Money + semantic identity execution + selected signature smoke.

NOT Gradle, KSP, Android, Room, JSON-runtime or a release/acceptance gate. Android/Room/
serialization collaborators are explicitly signature-only stubs in a temporary directory.
DTO/entity constructor signatures and enum declarations are extracted from this source tree.
The identity regression executes the real validator with a stubbed codec; it does not validate JSON/hash.
No stub code is installed in the application. Requires kotlinc and java; never downloads.
"""
from pathlib import Path
import hashlib, json, re, shutil, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
NET = ROOT / 'data/network/src/main/kotlin/com/verto/app/data/sync'
DB = ROOT / 'data/database/src/main/kotlin/com/verto/app/data/local'
PULL = ROOT / 'data/sync/src/main/kotlin/com/verto/app/data/sync/pull'
NAMES = ['Invoice','InvoiceItem','InvoiceDueInstallment','Payment','PaymentAllocation','RealizedFxEvent','InvoiceReturnDocument','InvoiceReturnLine','InvoiceReturnPaymentAllocation']
DTO_SOURCE = (NET / 'FinancialSyncContractV2.kt').read_text()
ENTITY_SOURCE = '\n'.join(p.read_text() for p in (DB / 'entity').glob('*.kt'))


def declaration(source, name):
    m = re.search(r'data class ' + name + r'\(\s*(.*?)\n\)', source, re.S)
    if not m: raise AssertionError('missing data class '+name)
    return m.group(0)


def props(source, name):
    return re.findall(r'\bval\s+(\w+)\s*:\s*([\w?]+(?:<[^>]+>)?\??)', declaration(source,name))


def main():
    kotlinc = shutil.which('kotlinc')
    if not kotlinc or not shutil.which('java'):
        raise SystemExit('NOT_RUN: kotlinc/java unavailable; no network fallback')
    with tempfile.TemporaryDirectory(prefix='b09-signature-smoke-') as temporary:
        tmp = Path(temporary)
        def write(name, text):
            path = tmp / name; path.write_text(text); return path
        entities = NAMES + ['InventoryMovement', 'InventoryCostRevision', 'CashRegisterMovement', 'ClientCredit', 'CommissionPayment', 'SyncEntityVersion', 'InventoryItem']
        body = []
        enums = set()
        for name in entities:
            fields = props(ENTITY_SOURCE, name+'Entity')
            body.append('data class '+name+'Entity(\n'+''.join('val '+k+': '+t+',\n' for k,t in fields)+')')
            enums.update(t for _,t in fields if t.rstrip('?') not in ['String','Long','Int','Boolean','Double','Float'] and not t.startswith('List'))
        for enum in sorted(enums):
            enum = enum.rstrip('?')
            m = re.search(r'enum class '+enum+r'(?:\([^\n]*\))?\s*\{[^}]*\}', ENTITY_SOURCE, re.S)
            if not m: raise AssertionError('enum declaration missing '+enum)
            # Keep labels and constructor parameters in actual enum declaration, excluding @Serializable.
            body.append(m.group(0))
        write('Entities.kt', 'package com.verto.app.data.local.entity\nconst val FUNCTIONAL_PER_TRANSACTION="FUNCTIONAL_PER_TRANSACTION"\n'+ '\n'.join(body))
        dtos = [declaration(DTO_SOURCE,n+'DtoV2') for n in NAMES]
        dtos += [declaration(DTO_SOURCE,n) for n in ['EffectReferenceV2','ExplicitTombstoneV2']]
        aggregate = declaration(DTO_SOURCE, 'FinancialAggregateSnapshotV2')
        aggregate += ' { companion object { fun serializer(): kotlinx.serialization.KSerializer<FinancialAggregateSnapshotV2> = error("signature-only") } }'
        dtos.append(aggregate)
        contract = (NET/'UnifiedSyncContract.kt').read_text()
        write('Dtos.kt', 'package com.verto.app.data.sync\nimport kotlinx.serialization.json.*\nconst val SYNC_REPAIR_CONTRACT_VERSION=2\nconst val SYNC_REPAIR_PAYLOAD_VERSION=2\nenum class SnapshotKindV2 { FULL }\nenum class SyncMutationOperation { UPSERT, DELETE }\n'+ '\n'.join(dtos)+'\n'+declaration(contract,'SyncChange')+'''
object SyncContractV2Codec {
    fun decodeFinancial(s:String):FinancialAggregateSnapshotV2 = error("signature-only")
    fun requireValid(s:FinancialAggregateSnapshotV2) = Unit
    fun financialBusinessHash(s:FinancialAggregateSnapshotV2):String = error("signature-only")
    fun <T> encode(value:T):String = error("signature-only")
}
''')
        write('Serialization.kt', '''package kotlinx.serialization
@RequiresOptIn annotation class ExperimentalSerializationApi
interface KSerializer<T> { val descriptor:kotlinx.serialization.descriptors.SerialDescriptor }
''')
        write('Descriptors.kt', '''package kotlinx.serialization.descriptors
open class SerialKind { object ENUM:SerialKind() }
class StructureKind { companion object { val CLASS=SerialKind(); val LIST=SerialKind() } }
class PrimitiveKind { companion object { val STRING=SerialKind(); val LONG=SerialKind(); val INT=SerialKind(); val BOOLEAN=SerialKind() } }
interface SerialDescriptor { val isNullable:Boolean; val kind:SerialKind; val elementsCount:Int
 fun getElementName(index:Int):String; fun getElementDescriptor(index:Int):SerialDescriptor }
''')
        write('Json.kt', '''package kotlinx.serialization.json
open class JsonElement
open class JsonPrimitive(val content:String, val isString:Boolean=true):JsonElement()
object JsonNull:JsonPrimitive("null",false)
class JsonObject(private val value:Map<String,JsonElement>):JsonElement(),Map<String,JsonElement> by value
class JsonArray(private val value:List<JsonElement>):JsonElement(),List<JsonElement> by value
val JsonElement.jsonObject:JsonObject get()=this as JsonObject
val JsonElement.jsonPrimitive:JsonPrimitive get()=this as JsonPrimitive
val JsonPrimitive.contentOrNull:String? get()=if(this===JsonNull)null else content
val JsonPrimitive.longOrNull:Long? get()=content.toLongOrNull()
val JsonPrimitive.intOrNull:Int? get()=content.toIntOrNull()
val JsonPrimitive.booleanOrNull:Boolean? get()=content.toBooleanStrictOrNull()
''')
        write('Inject.kt', 'package javax.inject\nannotation class Inject\nannotation class Singleton\n')
        write('Room.kt', '''package androidx.room
annotation class Insert(val onConflict:Int=0)
annotation class Update(val onConflict:Int=0)
annotation class Query(val value:String)
object OnConflictStrategy { const val ABORT=3 }
suspend fun <T> com.verto.app.data.local.AppDatabase.withTransaction(block:suspend ()->T):T = error("signature-only; not Room")
''')
        write('DaoSignatures.kt', '''package com.verto.app.data.local.dao
import com.verto.app.data.local.entity.*
interface InvoiceDao:FinancialMaterializationDao {
 suspend fun getInvoiceItemsSync(invoiceId:String):List<InvoiceItemEntity>
 suspend fun getDueInstallments(invoiceId:String):List<InvoiceDueInstallmentEntity>
}
interface PaymentDao {
 suspend fun getPaymentsForInvoiceSync(invoiceId:String):List<PaymentEntity>
 suspend fun getPaymentAllocationsForInvoiceSync(invoiceId:String):List<PaymentAllocationEntity>
 suspend fun getRealizedFxEventsForInvoiceSync(invoiceId:String):List<RealizedFxEventEntity>
}
interface InvoiceReturnDao {
 suspend fun getForInvoice(invoiceId:String):List<InvoiceReturnDocumentEntity>
 suspend fun getLines(returnId:String):List<InvoiceReturnLineEntity>
 suspend fun getPaymentAllocations(returnId:String):List<InvoiceReturnPaymentAllocationEntity>
}
interface InventoryDao { suspend fun getItemByIdSync(id:String):InventoryItemEntity? }
interface UnifiedSyncDao {
 suspend fun readEntityVersion(organizationId:String,scopeId:String,versionFamily:String,aggregateId:String):SyncEntityVersionEntity?
 suspend fun recordAppliedVersion(organizationId:String,scopeId:String,versionFamily:String,aggregateId:String,appliedVersion:Long,appliedRevision:Long,contentHash:String,tombstone:Boolean,updatedAt:Long):SyncEntityVersionEntity
}
''')
        write('Database.kt', '''package com.verto.app.data.local
import com.verto.app.data.local.dao.*
abstract class AppDatabase {
 abstract fun inTransaction():Boolean
 abstract fun invoiceDao():InvoiceDao
 abstract fun paymentDao():PaymentDao
 abstract fun invoiceReturnDao():InvoiceReturnDao
 abstract fun unifiedSyncDao():UnifiedSyncDao
 abstract fun inventoryDao():InventoryDao
}
''')
        write('Protection.kt', '''package com.verto.app.data.sync.ownership
data class ProtectedSyncKey(val type:String,val id:String)
class SyncPendingProtection { suspend fun isProtected(organizationId:String,key:ProtectedSyncKey,financialRootId:String?=null):Boolean = error("signature-only") }
''')
        write('Failure.kt', '''package com.verto.app.data.sync.pull
class UnifiedSyncPullFailure(val code:String,message:String,cause:Throwable?=null):IllegalStateException("$code: $message",cause)
''')
        fixture = json.loads((ROOT/'data/sync/src/androidTest/assets/sync/b09/financial-full-v2.json').read_text())
        fields = ['header','items','dueInstallments','payments','paymentAllocations','realizedFxEvents','returnDocuments','returnLines','returnPaymentAllocations']
        main = ['import com.verto.app.data.sync.*', 'import com.verto.app.utils.*', 'fun main() {', 'var checks=0']
        for i,(name,field) in enumerate(zip(NAMES,fields)):
            obj = fixture[field] if i == 0 else fixture[field][0]
            def literal(value,typ):
                if value is None: return 'null'
                if typ == 'String' or typ == 'String?': return json.dumps(value,ensure_ascii=False).replace('$','\\$')
                if typ == 'Long': return str(value)+'L'
                if typ == 'Boolean': return str(value).lower()
                return str(value)
            pairs=props(DTO_SOURCE,name+'DtoV2')
            main.append('val dto'+str(i)+' = '+name+'DtoV2('+','.join(k+'='+literal(obj[k],t) for k,t in pairs)+')')
            main.append(f'check(dto{i}.toRemoteEntityV2().toDtoV2() == dto{i}); checks += {len(pairs)}')
            for k,t in pairs:
                if t == 'Long' and k.endswith('Minor'):
                    main.append(f'for (value in listOf(Long.MIN_VALUE,Long.MAX_VALUE,9_007_199_254_740_993L,-1L,0L)) {{ val edge=dto{i}.copy({k}=value); check(edge.toRemoteEntityV2().toDtoV2()==edge); checks++ }}')
                if t == 'String?':
                    main.append(f'for (value in listOf(null,"")) {{ val edge=dto{i}.copy({k}=value); check(edge.toRemoteEntityV2().toDtoV2()==edge); checks++ }}')
        # Execute the actual receive-side semantic validator as a supplementary regression.
        # Codec/serializer collaborators above remain signature stubs: this is NOT a JSON gate.
        def kotlin_value(value, typ):
            if value is None:
                return 'null'
            typ = typ.rstrip('?')
            if typ.startswith('List<'):
                child = typ[5:-1]
                return 'listOf(' + ','.join(kotlin_value(item, child) for item in value) + ')'
            if typ == 'String':
                return json.dumps(value, ensure_ascii=False).replace('$', '\\$')
            if typ == 'Long':
                return str(value) + 'L'
            if typ == 'Int':
                return str(value)
            if typ == 'Boolean':
                return str(value).lower()
            if typ == 'SnapshotKindV2':
                return 'SnapshotKindV2.' + value
            return typ + '(' + ','.join(key + '=' + kotlin_value(value[key], child)
                for key, child in props(DTO_SOURCE, typ)) + ')'
        main.append('val baseline = ' + kotlin_value(fixture, 'FinancialAggregateSnapshotV2'))
        main += [
            'com.verto.app.data.sync.pull.FinancialMaterializationContractV2.validate(baseline)',
            'val original = baseline.payments.single { it.reversedPaymentId == null }',
            'val reversal = baseline.payments.single { it.reversedPaymentId != null }',
            'val sharedWritePayments = (baseline.payments + original.copy(id="second-original", writeId="original-write-2") + reversal.copy(id="second-reversal", reversedPaymentId="second-original")).sortedBy { it.id }',
            'val sharedWriteRefs = sharedWritePayments.map { EffectReferenceV2("financial_outbox", "PAYMENT", it.id, it.writeId.ifBlank { it.id }, "a".repeat(64)) }.sortedWith(compareBy<EffectReferenceV2> { it.owner }.thenBy { it.factType }.thenBy { it.factId })',
            'val sharedWrite = baseline.copy(payments=sharedWritePayments, effectReferences=sharedWriteRefs)',
            'check(sharedWrite.payments.count { it.writeId == reversal.writeId } == 2)',
            'println("REGRESSION: two different reversals sharing one legal writeId must be accepted")',
            'com.verto.app.data.sync.pull.FinancialMaterializationContractV2.validate(sharedWrite)',
            'var identityChecks = 1',
            'val duplicate = sharedWrite.copy(effectReferences=sharedWrite.effectReferences + sharedWrite.effectReferences.first())',
            'try { com.verto.app.data.sync.pull.FinancialMaterializationContractV2.validate(duplicate); error("duplicate payment fact accepted") } catch (failure:com.verto.app.data.sync.pull.UnifiedSyncPullFailure) { check(failure.code == "CONTRACT_FIELD_INVALID"); identityChecks++ }',
            'val nonPaymentRefs = sharedWrite.effectReferences + listOf(EffectReferenceV2("inventory_stock_outbox", "INVENTORY_MOVEMENT", "movement-a", "same-key", "a".repeat(64)), EffectReferenceV2("inventory_stock_outbox", "INVENTORY_MOVEMENT", "movement-b", "same-key", "b".repeat(64)))',
            'val nonPaymentDuplicate = sharedWrite.copy(effectReferences=nonPaymentRefs.sortedWith(compareBy<EffectReferenceV2> { it.owner }.thenBy { it.factType }.thenBy { it.factId }))',
            'try { com.verto.app.data.sync.pull.FinancialMaterializationContractV2.validate(nonPaymentDuplicate); error("duplicate stock business identity accepted") } catch (failure:com.verto.app.data.sync.pull.UnifiedSyncPullFailure) { check(failure.code == "CONTRACT_FIELD_INVALID"); identityChecks++ }',
            'println("SHARED_WRITE_ID_SEMANTIC_REGRESSION=PASS; IDENTITY_RULE_ASSERTIONS=$identityChecks; CODEC_STUBBED=true; ROOM=NOT_RUN")',
        ]
        main += ['check(normalizeSupplierInvoiceReference(" Ab-١٢ ")=="ab١٢");checks++',
                 'try { dto0.copy(category="NOT_A_CATEGORY").toRemoteEntityV2(); error("enum accepted") } catch (_:IllegalArgumentException) { checks++ }',
                 'println("REAL_MAPPING_MONEY_ASSERTIONS=$checks; NINE_DTOS=PASS; SIGNATURE_SMOKE=PASS; ROOM=NOT_RUN; JSON_RUNTIME=NOT_RUN")','}']
        write('Main.kt','\n'.join(main))
        actual = [NET/'FinancialEntityMappingsV2.kt', DB/'dao/FinancialMaterializationDao.kt']
        actual += [PULL/x for x in ['UnifiedRemoteMaterialization.kt','FinancialMaterializationContractV2.kt','FinancialMaterializerV2.kt','FinancialEffectVerifierV2.kt']]
        actual += [ROOT/'core/common/src/main/kotlin/com/verto/app'/x for x in ['money/Money.kt','utils/SearchTextNormalizer.kt','utils/SupplierInvoiceReferenceNormalizer.kt']]
        command=[kotlinc,*map(str,sorted(tmp.glob('*.kt'))+actual),'-include-runtime','-d',str(tmp/'smoke.jar')]
        print('LIMITATION: collaborator stubs; not a Gradle/KSP/Room/JSON runtime build.',flush=True)
        subprocess.run(command,check=True,timeout=100)
        subprocess.run(['java','-jar',str(tmp/'smoke.jar')],check=True,timeout=20)
        print('ACTUAL_SOURCES_SHA256='+hashlib.sha256(''.join(hashlib.sha256(p.read_bytes()).hexdigest() for p in actual).encode()).hexdigest())

if __name__ == '__main__': main()
