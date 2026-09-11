#!/usr/bin/env python3
"""Offline B10 verification: execute real pure Kotlin policy tests; separately type-check production
receive/apply/DAO/migration/observer code and new test signatures using explicit collaborator stubs.
The stubs NEVER enter the app. This is NOT Gradle/KSP/Room/JSON runtime/Hilt processing or G-B10.
Network and database modules compile to separate jars, preserving cross-module smart-cast checks.
"""
from pathlib import Path
import re, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
DB=ROOT/'data/database/src/main/kotlin/com/verto/app/data/local'
NET=ROOT/'data/network/src/main/kotlin/com/verto/app/data/sync'
SYNC=ROOT/'data/sync/src/main/kotlin/com/verto/app/data/sync'
PULL=SYNC/'pull'
TEST=ROOT/'data/sync/src/test/kotlin/com/verto/app/data/sync/pull'
ANDROID_TEST=ROOT/'data/sync/src/androidTest/kotlin/com/verto/app/data/sync/pull'

def run(args):
    result=subprocess.run([str(a) for a in args],capture_output=True,text=True,timeout=100)
    if result.returncode:
        print(result.stdout); print(result.stderr); raise SystemExit(result.returncode)
    return result.stdout

def main():
    compiler=shutil.which('kotlinc'); java=shutil.which('java'); javac=shutil.which('javac')
    if not all([compiler,java,javac]): raise SystemExit('NOT_RUN: Kotlin/JDK absent; no download attempted')
    coroutines=Path(compiler).resolve().parents[1]/'lib/kotlinx-coroutines-core-jvm.jar'
    if not coroutines.exists(): raise SystemExit('NOT_RUN: locally bundled coroutines absent')
    print(run([compiler,'-version']).strip(),flush=True)
    with tempfile.TemporaryDirectory(prefix='b10-offline-') as d:
        temp=Path(d)
        def write(name,source):
            p=temp/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(source); return p
        assertjava=write('Assert.java','''package org.junit;
public class Assert {
 public static void assertTrue(boolean b){if(!b)throw new AssertionError("expected true");}
 public static void assertFalse(boolean b){if(b)throw new AssertionError("expected false");}
 public static void assertNull(Object v){if(v!=null)throw new AssertionError("expected null: "+v);}
 public static void assertNotNull(Object v){if(v==null)throw new AssertionError("expected nonnull");}
 public static void assertEquals(Object a,Object b){if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 public static void assertEquals(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
 public static void assertEquals(String msg,long a,long b){assertEquals(a,b);}
 public static void fail(String msg){throw new AssertionError(msg);}
}
''')
        classes=temp/'classes'; classes.mkdir(); run([javac,'-d',classes,assertjava])
        junit=write('JUnit.kt','''package org.junit
@Retention(AnnotationRetention.RUNTIME) annotation class Test
annotation class Before
annotation class After
''')
        runner=write('PolicyMain.kt','''import com.verto.app.data.sync.pull.*
fun main(){
 val target=DurableInboxPolicyTest()
 val methods=target.javaClass.declaredMethods.filter{it.getAnnotation(org.junit.Test::class.java)!=null}.sortedBy{it.name}
 for(method in methods){method.invoke(target);println("PASS policy: ${method.name}")}
 println("${methods.size} real policy test methods executed. JUnit assertion-only shim; no Android/Room/JSON claim.")
}
''')
        run([compiler,PULL/'DurableInboxPolicy.kt',TEST/'DurableInboxPolicyTest.kt',junit,runner,'-cp',classes,'-include-runtime','-d',temp/'policy.jar'])
        print(run([java,'-cp',f'{temp}/policy.jar:{classes}','PolicyMainKt']),flush=True)
        # Base annotation/JSON/Android/Room signatures. None implement business semantics.
        base=[]
        def basewrite(name,source): base.append(write(name,source))
        basewrite('Serialization.kt','''package kotlinx.serialization
annotation class Serializable
annotation class SerialName(val value:String)
interface KSerializer<T>
inline fun <reified T> kotlinx.serialization.json.Json.decodeFromString(value:String):T=error("signature-only")
inline fun <reified T> kotlinx.serialization.json.Json.encodeToString(value:T):String=error("signature-only")
fun <T> kotlinx.serialization.json.Json.encodeToString(serializer:KSerializer<T>,value:T):String=error("signature-only")
''')
        basewrite('Json.kt','''package kotlinx.serialization.json
open class JsonElement { companion object { fun serializer():kotlinx.serialization.KSerializer<JsonElement> = error("signature-only") } }
open class JsonPrimitive(val content:String,val isString:Boolean=true):JsonElement()
fun JsonPrimitive(v:Number):JsonPrimitive=JsonPrimitive(v.toString(),false)
fun JsonPrimitive(v:Boolean):JsonPrimitive=JsonPrimitive(v.toString(),false)
object JsonNull:JsonPrimitive("null",false)
class JsonObject(private val data:Map<String,JsonElement>):JsonElement(),Map<String,JsonElement> by data
class JsonArray(private val data:List<JsonElement>):JsonElement(),List<JsonElement> by data
class JsonBuilder { var encodeDefaults=false;var explicitNulls=false }
class Json(block:JsonBuilder.()->Unit={}) { init{JsonBuilder().block()} ; fun parseToJsonElement(s:String):JsonElement=error("signature-only") }
inline fun <reified T> Json.encodeToJsonElement(value:T):JsonElement=error("signature-only")
val JsonPrimitive.contentOrNull:String? get()=if(this===JsonNull)null else content
val JsonPrimitive.longOrNull:Long? get()=content.toLongOrNull()
val JsonPrimitive.intOrNull:Int? get()=content.toIntOrNull()
val JsonPrimitive.booleanOrNull:Boolean? get()=content.toBooleanStrictOrNull()
val JsonElement.jsonObject:JsonObject get()=this as JsonObject
class JsonObjectBuilder { val values=mutableMapOf<String,JsonElement>();fun put(key:String,value:JsonElement){values[key]=value} }
fun JsonObjectBuilder.put(key:String,value:String){put(key,JsonPrimitive(value))}
fun JsonObjectBuilder.put(key:String,value:Number){put(key,JsonPrimitive(value))}
fun JsonObjectBuilder.put(key:String,value:Boolean){put(key,JsonPrimitive(value))}
fun buildJsonObject(block:JsonObjectBuilder.()->Unit):JsonObject=JsonObjectBuilder().also(block).let{JsonObject(it.values)}
''')
        basewrite('Inject.kt','package javax.inject\nannotation class Inject\nannotation class Singleton\n')
        basewrite('Room.kt','''package androidx.room
import kotlin.reflect.KClass
annotation class Entity(val tableName:String="",val primaryKeys:Array<String> = [],val indices:Array<Index> = [])
annotation class Index(val value:Array<String>,val unique:Boolean=false,val name:String="")
annotation class ColumnInfo(val name:String="",val defaultValue:String="")
annotation class PrimaryKey(val autoGenerate:Boolean=false)
annotation class Insert(val onConflict:Int=0)
annotation class Query(val value:String)
annotation class Transaction
object OnConflictStrategy {const val IGNORE=5;const val ABORT=3}
open class InvalidationTracker {abstract class Observer(vararg val tables:String){abstract fun onInvalidated(tables:Set<String>)};fun addObserver(o:Observer){} }
abstract class RoomDatabase {val invalidationTracker=InvalidationTracker();fun close(){} }
suspend fun <T> RoomDatabase.withTransaction(block:suspend ()->T):T=error("signature-only; NOT Room")
object Room {fun <T:RoomDatabase> databaseBuilder(context:android.content.Context,clazz:Class<T>,name:String)=Builder<T>()
 class Builder<T:RoomDatabase>{fun allowMainThreadQueries()=this;fun build():T=error("signature-only")}}
''')
        basewrite('Sqlite.kt','''package androidx.sqlite.db
interface SupportSQLiteDatabase {
 fun execSQL(sql:String)
 fun execSQL(sql:String,args:Array<out Any?>)
 fun query(sql:String):Cursor
 fun query(sql:String,args:Array<out Any?>):Cursor
}
interface Cursor:java.io.Closeable {fun moveToFirst():Boolean;fun getLong(i:Int):Long;fun getString(i:Int):String}
interface SupportSQLiteOpenHelper {val readableDatabase:SupportSQLiteDatabase;val writableDatabase:SupportSQLiteDatabase}
''')
        basewrite('Migration.kt','package androidx.room.migration\nabstract class Migration(val startVersion:Int,val endVersion:Int){abstract fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase)}\n')
        basewrite('AndroidSql.kt','package android.database.sqlite\nclass SQLiteFullException:RuntimeException()\n')
        basewrite('Context.kt','package android.content\nopen class Context { val filesDir=java.io.File(".");fun deleteDatabase(name:String)=true }\n')
        basewrite('StatFs.kt','package android.os\nclass StatFs(path:String){val availableBytes:Long=0}\n')
        basewrite('Dagger.kt','package dagger\nannotation class Binds\nannotation class Module\n')
        basewrite('Hilt.kt','package dagger.hilt\nannotation class InstallIn(vararg val value:kotlin.reflect.KClass<*>)\n')
        basewrite('HiltComponent.kt','package dagger.hilt.components\nclass SingletonComponent\n')
        basewrite('HiltContext.kt','package dagger.hilt.android.qualifiers\nannotation class ApplicationContext\n')
        basewrite('AppProvider.kt','package androidx.test.core.app\nobject ApplicationProvider {fun <T:android.content.Context> getApplicationContext():T=error("signature-only")}\n')
        basewrite('AndroidRunner.kt','package androidx.test.ext.junit.runners\nclass AndroidJUnit4\n')
        basewrite('JUnitRunner.kt','package org.junit.runner\nannotation class RunWith(val value:kotlin.reflect.KClass<*>)\n')
        base.append(junit)
        run([compiler,*base,'-cp',classes,'-d',temp/'base.jar'])
        netfiles=[NET/'UnifiedSyncContract.kt', NET/'UnifiedSyncAggregateRegistry.kt', NET/'SyncInboxProtocolV2.kt']
        run([compiler,*netfiles,'-cp',temp/'base.jar','-d',temp/'network.jar'])
        dbstub=write('DaoStub.kt','''package com.verto.app.data.local.dao
import com.verto.app.data.local.entity.*
enum class UnifiedInboxInsertResult{INSERTED,DUPLICATE}
abstract class UnifiedSyncDao:DurableSyncInboxDao {
 abstract suspend fun getCursor(scopeId:String):SyncCursorEntity?
 abstract suspend fun insertInitialCursor(row:SyncCursorEntity)
 abstract suspend fun getOutbox(id:String):SyncOutboxEntity?
 abstract suspend fun insertOutboxRaw(row:SyncOutboxEntity):Long
 abstract suspend fun insertInboxChecked(row:SyncInboxEntity):UnifiedInboxInsertResult
 abstract suspend fun recordObservedVersion(org:String,scope:String,family:String,id:String,version:Long?,at:Long):SyncEntityVersionEntity
 abstract suspend fun recordAppliedVersion(org:String,scope:String,family:String,id:String,version:Long,revision:Long,hash:String,tombstone:Boolean,at:Long):SyncEntityVersionEntity
 abstract suspend fun readEntityVersion(org:String,scope:String,family:String,id:String):SyncEntityVersionEntity?
 abstract suspend fun getActiveCursorForPrincipal(org:String,principal:String,family:String,version:Int):SyncCursorEntity?
 abstract suspend fun invalidateCursor(scope:String,org:String,principal:String,family:String,version:Int,definition:Int,at:Long):Int
 abstract suspend fun markBootstrapRequired(scope:String,org:String,principal:String,family:String,version:Int,definition:Int,at:Long):Int
}
interface SyncRecoveryDao {
 suspend fun hasAppliedInboxAnchor(scopeId:String,revision:Long):Boolean
 suspend fun getRecoveryState(scopeId:String):SyncRecoveryStateEntity?
 suspend fun upsertRecoveryState(row:SyncRecoveryStateEntity)
}
interface CategoryDao {suspend fun insertCategory(row:CategoryEntity)}
''')
        dbclass=write('DatabaseStub.kt','''package com.verto.app.data.local
import com.verto.app.data.local.dao.*
abstract class AppDatabase:androidx.room.RoomDatabase(){
 abstract fun inTransaction():Boolean
 abstract fun unifiedSyncDao():UnifiedSyncDao
 abstract fun syncRecoveryDao():SyncRecoveryDao
 abstract fun categoryDao():CategoryDao
 abstract val openHelper:androidx.sqlite.db.SupportSQLiteOpenHelper
}
''')
        dbfiles=[DB/'entity'/n for n in ['UnifiedSyncEntities.kt','SyncRepairV2Entities.kt','SyncDurableInboxEntities.kt','SyncRecoveryEntities.kt','CategoryEntity.kt']]
        dbfiles += [DB/'dao/DurableSyncInboxDao.kt',DB/'AppDatabaseMigrations98To99.kt',dbstub,dbclass]
        run([compiler,*dbfiles,'-cp',temp/'base.jar','-d',temp/'database.jar'])
        stubs=[]
        def stub(name,source): stubs.append(write(name,source))
        stub('SyncStubs.kt','''package com.verto.app.data.sync
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.ownership.SyncPendingProtection
object SyncContractV2Codec {val json=kotlinx.serialization.json.Json()}
object UnifiedStrongerBridgeRegistry {fun validateRemoteChange(change:SyncChange){}}
interface UnifiedSyncPullRemote {suspend fun resolveScope():SyncScope;suspend fun pull(scope:SyncScope,afterCursor:String,limit:Int):SyncPullPage}
fun sha256Utf8(value:String)=java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
enum class FrozenAckOutcome {ACKNOWLEDGED_CURRENT,ACKNOWLEDGED_LOCAL_CHANGED,STALE_LEASE,RECEIPT_MISMATCH}
class FrozenMutationStore(db:AppDatabase,p:SyncPendingProtection){suspend fun acknowledgeAuthoritativeEcho(org:String,id:String,type:String,aggregate:String,hash:String,revision:Long,version:Long?,at:Long):FrozenAckOutcome=error("signature-only")}
data class SyncWorkScope(val organizationId:String,val userId:String,val epoch:Long){val stableKey="$organizationId:$userId:$epoch"}
interface SyncWakeScheduler {fun enqueueInboxContinuation(scope:SyncWorkScope,delayMillis:Long)}
''')
        stub('ProtectionStubs.kt','''package com.verto.app.data.sync.ownership
import com.verto.app.data.local.AppDatabase
data class ProtectedSyncKey(val type:String,val id:String)
enum class SyncSourceOwner {UNIFIED}
data class PendingSourceRef(val organizationId:String,val owner:SyncSourceOwner,val sourceId:String)
class SyncPendingProtection(db:AppDatabase) {
 suspend fun isProtected(org:String,key:ProtectedSyncKey,financialRootId:String?=null,projectionRoot:ProtectedSyncKey?=null):Boolean=error("signature-only")
 suspend fun capture(ref:PendingSourceRef,keys:Collection<ProtectedSyncKey>,gen:Long,hash:String,kind:String){}
 suspend fun releaseAfterTerminal(ref:PendingSourceRef):Int=error("signature-only")
}
''')
        stub('ApplierStubs.kt','''package com.verto.app.data.sync.pull
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.ownership.SyncPendingProtection
class FinancialApplyBatchV2
class FinancialEffectVerifierV2(db:AppDatabase,p:SyncPendingProtection)
class FinancialMaterializerV2(db:AppDatabase,p:SyncPendingProtection,v:FinancialEffectVerifierV2)
class UnifiedStrongerSyncChangeApplier(db:AppDatabase,f:FinancialMaterializerV2)
class UnifiedSyncChangeApplier(db:AppDatabase,s:UnifiedStrongerSyncChangeApplier){
 fun beginFinancialBatch(org:String,scope:String):FinancialApplyBatchV2=error("signature-only")
 suspend fun apply(change:SyncChange,batch:FinancialApplyBatchV2){}
 suspend fun completeFinancialBatch(batch:FinancialApplyBatchV2){}
}
''')
        newfiles=[PULL/n for n in ['DurableInboxPolicy.kt','DurableInboxPageValidator.kt','DurableInboxApplyCoordinator.kt',
            'DurableInboxEchoReconciler.kt','DurableInboxWakeObserver.kt','InboxStorageProbe.kt','UnifiedSyncInboxMapper.kt',
            'UnifiedSyncPullEngine.kt','UnifiedSyncPullRegistry.kt']]
        cp=':'.join(str(x) for x in [temp/'base.jar',temp/'network.jar',temp/'database.jar',classes,coroutines])
        run([compiler,*newfiles,*stubs,TEST/'DurableInboxPageValidatorTest.kt',ANDROID_TEST/'DurableInboxApplyCoordinatorInstrumentedTest.kt',
            '-cp',cp,'-d',temp/'main-check.jar'])
        print('PASS: separate-module Kotlin signature compile: actual network DTO/registry, actual Room entities/DAO/migration,',flush=True)
        print('actual receive/apply/checkpoint/echo/wake/storage/engine/mapper + 12 validator and 14 Room-test signatures.',flush=True)
        print('Collaborators are EXPLICIT signature stubs. JSON/Room/Hilt/Gradle execution NOT_RUN. G-B10 remains BLOCKED.',flush=True)

if __name__=='__main__': main()
