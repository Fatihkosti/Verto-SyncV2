#!/usr/bin/env python3
from __future__ import annotations

import copy
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[3]

def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec); assert spec and spec.loader; spec.loader.exec_module(mod); return mod

guard = load_module("verto_arch_guard_test", PROJECT / "tools/architecture/verto_arch_guard.py")
complexity = load_module("verto_complexity_guard_test", PROJECT / "tools/architecture/complexity_guard.py")
change = load_module("verto_change_contract_test", PROJECT / "scripts/ci/verify-change-contract.py")

class Fixture:
    def __init__(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="verto-arch-guard-")
        self.root = Path(self.tmp.name)
        self._write_base()
        self.refresh_manifests()
        self.contract = json.loads((PROJECT / "docs/architecture/contracts/architecture-contracts.json").read_text())
        self.rule_base = {"rules": [{k:r[k] for k in ("id","title","severity","prohibited_state")} for r in self.contract["rules"]]}
        self.debt = {"architecture":{"violations":[]},"kotlin_quality":{"metrics":{},"evidence":{}}}
        self.governance = {"kotlin_quality_metrics":{},"legacy_architecture_identities":[]}
        self.exceptions = {"exceptions":[]}
        self.ownership = {
            "entities":[self.owner("SeedEntity","ROOM_ENTITY")],
            "tables":[self.owner("seed","ROOM_TABLE")],
            "daos":[self.owner("SeedDao","DAO")],
            "repositories":[],
        }
        self.dep = self.dependency_contract()
    def close(self): self.tmp.cleanup()
    def write(self, path: str, text: str):
        p=self.root/path; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text,encoding="utf-8")
    def owner(self, identity: str, kind: str):
        return {"identity":identity,"kind":kind,"owner_module":":data:database","owner_feature_or_infrastructure":"infrastructure::data:database"}
    def _write_base(self):
        self.write("settings.gradle.kts", 'include(":data:database", ":feature:a", ":feature:b")\n')
        for module in ["data/database","feature/a","feature/b"]: self.write(f"{module}/build.gradle.kts", "dependencies {\n}\n")
        self.write("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt", "package com.verto.app.data.local\nconst val ROOM_SCHEMA_VERSION: Int = 81\n")
        self.write("data/database/src/main/kotlin/com/verto/app/data/local/entity/SeedEntity.kt", "package com.verto.app.data.local.entity\n@Entity(tableName=\"seed\")\ndata class SeedEntity(val id:String)\n")
        self.write("data/database/src/main/kotlin/com/verto/app/data/local/dao/SeedDao.kt", "package com.verto.app.data.local.dao\n@Dao\ninterface SeedDao\n")
        schema={"database":{"entities":[{"tableName":"seed"}]}}
        self.write("app/schemas/com.verto.app.data.local.AppDatabase/81.json", json.dumps(schema))
        self.write("feature/a/src/main/kotlin/com/verto/app/feature/a/domain/APublic.kt", "package com.verto.app.feature.a.domain\nclass APublic\n")
        self.write("feature/a/src/main/kotlin/com/verto/app/feature/a/application/AService.kt", "package com.verto.app.feature.a.application\nclass AService\n")
        self.write("feature/b/src/main/kotlin/com/verto/app/feature/b/domain/BPublic.kt", "package com.verto.app.feature.b.domain\nclass BPublic\n")
        self.write("feature/b/src/main/kotlin/com/verto/app/feature/b/data/InternalStore.kt", "package com.verto.app.feature.b.data\nclass InternalStore\n")
    def refresh_manifests(self):
        for module in [":feature:a",":feature:b"]:
            ext=guard.parse_external_graph(self.root, guard.parse_modules(self.root)).get(module,[])
            pub = "com.verto.app.feature.a.domain.APublic" if module.endswith(":a") else "com.verto.app.feature.b.domain.BPublic"
            d={
                "feature":{"name":module.lstrip(":").replace(":","/"),"module":module,"owner":module,"purpose":"fixture"},
                "architecture":{"public_api":[pub],"internal_packages":[guard.package_prefix_for_module(module)],"exposed_ports":[],"incoming_dependencies":[],"outgoing_dependencies":[],"forbidden_dependencies":["ALL_UNDECLARED_PROJECT_DEPENDENCIES"],"external_dependencies":ext},
                "data":{"owned_entities":[],"owned_tables":[],"owned_repositories":[]},
                "integration":{"allowed_cross_feature_ports":[],"allowed_public_api_dependencies":[],"legacy_cross_feature_debt":[]},
                "verification":{"verified_against":"fixture","source_sha256":"0"*64,"public_api_surface_hash":guard.public_surface_hash(module,self.root),"public_api_surface_algorithm":"verto-top-level-public-surface-v1"}
            }
            p=guard.manifest_path_for_module(self.root,module); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(d),encoding="utf-8")
    def dependency_contract(self):
        mods=guard.parse_modules(self.root); graph=guard.parse_module_graph(self.root,mods); ext=guard.parse_external_graph(self.root,mods)
        return {"modules":[{"module":m,"direct_project_dependencies":[{"target":t} for t in graph[m]],"external_dependencies":[{"identity":x} for x in ext[m]]} for m in mods]}
    def configure_public_api_edge(self):
        self.write("settings.gradle.kts", 'include(":data:database", ":feature:a", ":feature:b", ":feature:b:api")\n')
        self.write("feature/b/api/build.gradle.kts", "dependencies {\n}\n")
        self.write("feature/b/api/src/main/kotlin/com/verto/app/feature/b/api/BApi.kt", "package com.verto.app.feature.b.api\nclass BApi\n")
        self.write("feature/a/build.gradle.kts", 'dependencies { implementation(project(":feature:b:api")) }\n')
        self.write("feature/a/src/main/kotlin/com/verto/app/feature/a/application/AService.kt", "package com.verto.app.feature.a.application\nimport com.verto.app.feature.b.api.BApi\nclass AService(val api: BApi? = null)\n")
        api_manifest = {
            "feature":{"name":"feature/b/api","module":":feature:b:api","owner":":feature:b","purpose":"fixture public API"},
            "architecture":{"public_api":["com.verto.app.feature.b.api.BApi"],"internal_packages":[],"exposed_ports":[],"incoming_dependencies":[":feature:a"],"outgoing_dependencies":[],"forbidden_dependencies":["ALL_UNDECLARED_PROJECT_DEPENDENCIES"],"external_dependencies":[]},
            "data":{"owned_entities":[],"owned_tables":[],"owned_repositories":[]},
            "integration":{"allowed_cross_feature_ports":[],"allowed_public_api_dependencies":[],"legacy_cross_feature_debt":[]},
            "verification":{"verified_against":"fixture","source_sha256":"0"*64,"public_api_surface_hash":guard.public_surface_hash(":feature:b:api",self.root),"public_api_surface_algorithm":"verto-top-level-public-surface-v1"},
        }
        guard.manifest_path_for_module(self.root,":feature:b:api").write_text(json.dumps(api_manifest),encoding="utf-8")
        a_path=guard.manifest_path_for_module(self.root,":feature:a"); a=json.loads(a_path.read_text())
        a["architecture"]["outgoing_dependencies"]=[":feature:b:api"]
        a["integration"]["allowed_public_api_dependencies"]=[":feature:b:api"]
        a["verification"]["public_api_surface_hash"]=guard.public_surface_hash(":feature:a",self.root)
        a_path.write_text(json.dumps(a),encoding="utf-8")
        self.dep=self.dependency_contract()
    def verify(self):
        mods=guard.parse_modules(self.root); mans,_,_=guard.load_manifests(self.root,mods)
        return guard.verify_project(self.root,self.contract,self.dep,self.ownership,self.debt,self.rule_base,self.governance,self.exceptions,None)
    def assert_pass(self, tc):
        r=self.verify(); tc.assertEqual("PASS",r["status"],r.get("failures"))

class ArchitectureGuardRegressionTests(unittest.TestCase):
    def setUp(self): self.fx=Fixture(); self.fx.assert_pass(self)
    def tearDown(self): self.fx.close()
    def fail_code(self, code: str):
        r=self.fx.verify(); self.assertEqual("FAIL",r["status"]); self.assertTrue(any(x.get("code")==code for x in r["failures"]),r["failures"])
        return r
    def recover(self, path: str, original: str):
        self.fx.write(path,original); self.fx.assert_pass(self)

    def test_foreign_storage_import_fails_and_recovers(self):
        path="feature/a/src/main/kotlin/com/verto/app/feature/a/application/AService.kt"; old=(self.fx.root/path).read_text()
        self.fx.write(path,old+"import com.verto.app.feature.b.data.InternalStore\n")
        self.fail_code("FAIL_ARCHITECTURE_GUARD"); self.recover(path,old)

    def test_application_to_dao_fails_and_recovers(self):
        path="feature/a/src/main/kotlin/com/verto/app/feature/a/application/AService.kt"; old=(self.fx.root/path).read_text()
        self.fx.write(path,"package com.verto.app.feature.a.application\nimport com.verto.app.data.local.dao.SeedDao\nclass AService\n")
        self.fail_code("FAIL_ARCHITECTURE_GUARD"); self.recover(path,old)

    def test_undeclared_project_dependency_fails_and_recovers(self):
        path="feature/a/build.gradle.kts"; old=(self.fx.root/path).read_text(); self.fx.write(path,'dependencies { implementation(project(":feature:b")) }\n')
        self.fail_code("FAIL_UNDECLARED_DEPENDENCY"); self.recover(path,old)

    def test_undeclared_external_dependency_fails_and_recovers(self):
        path="feature/a/build.gradle.kts"; old=(self.fx.root/path).read_text(); self.fx.write(path,'dependencies {\n    implementation("x:y:1")\n}\n')
        self.fail_code("FAIL_UNDECLARED_EXTERNAL_DEPENDENCY"); self.recover(path,old)

    def test_missing_manifest_fails_and_recovers(self):
        p=guard.manifest_path_for_module(self.fx.root,":feature:b"); old=p.read_text(); p.unlink(); self.fail_code("FAIL_MISSING_FEATURE_MANIFEST"); p.write_text(old); self.fx.assert_pass(self)

    def test_public_api_change_without_manifest_update_fails_and_recovers(self):
        path="feature/b/src/main/kotlin/com/verto/app/feature/b/domain/BPublic.kt"; old=(self.fx.root/path).read_text(); self.fx.write(path,old+"class NewPublicApi\n")
        self.fail_code("FAIL_PUBLIC_API_UNDECLARED"); self.recover(path,old)

    def test_missing_and_duplicate_data_owner_fail(self):
        self.fx.write("data/database/src/main/kotlin/com/verto/app/data/local/entity/NewEntity.kt", "package com.verto.app.data.local.entity\n@Entity(tableName=\"new\")\nclass NewEntity\n")
        self.fail_code("FAIL_DATA_OWNER_MISSING")
        (self.fx.root/"data/database/src/main/kotlin/com/verto/app/data/local/entity/NewEntity.kt").unlink(); self.fx.assert_pass(self)
        self.fx.ownership["daos"].append(copy.deepcopy(self.fx.ownership["daos"][0])); self.fail_code("FAIL_DATA_OWNER_DUPLICATED")
        self.fx.ownership["daos"].pop(); self.fx.assert_pass(self)

    def test_foreign_internal_import_fails_and_recovers(self):
        self.fx.write("feature/b/src/main/kotlin/com/verto/app/feature/b/domain/InternalThing.kt", "package com.verto.app.feature.b.domain\nclass InternalThing\n")
        self.fx.refresh_manifests()  # bind source surface while keeping InternalThing absent from public_api
        path="feature/a/src/main/kotlin/com/verto/app/feature/a/application/AService.kt"; old=(self.fx.root/path).read_text()
        self.fx.write(path,"package com.verto.app.feature.a.application\nimport com.verto.app.feature.b.domain.InternalThing\nclass AService\n")
        self.fail_code("FAIL_ARCHITECTURE_GUARD"); self.recover(path,old)

    def test_declared_public_api_module_and_public_symbol_pass(self):
        self.fx.configure_public_api_edge(); self.fx.assert_pass(self)
        a_path=guard.manifest_path_for_module(self.fx.root,":feature:a"); a=json.loads(a_path.read_text())
        a["integration"]["allowed_public_api_dependencies"]=[]; a_path.write_text(json.dumps(a),encoding="utf-8")
        r=self.fx.verify(); self.assertEqual("FAIL",r["status"]); self.assertEqual(1,r["scan"]["rule_counts"].get("VARCH-012"))
        a["integration"]["allowed_public_api_dependencies"]=[":feature:b:api"]; a_path.write_text(json.dumps(a),encoding="utf-8"); self.fx.assert_pass(self)

    def test_declared_implementation_module_edge_still_fails(self):
        path="feature/a/build.gradle.kts"; old=(self.fx.root/path).read_text(); manifest_path=guard.manifest_path_for_module(self.fx.root,":feature:a")
        old_manifest=manifest_path.read_text(); old_dep=copy.deepcopy(self.fx.dep)
        self.fx.write(path,'dependencies { implementation(project(":feature:b")) }\n')
        a=json.loads(manifest_path.read_text()); a["architecture"]["outgoing_dependencies"]=[":feature:b"]; a["integration"]["allowed_public_api_dependencies"]=[":feature:b"]; manifest_path.write_text(json.dumps(a),encoding="utf-8")
        self.fx.dep=self.fx.dependency_contract(); r=self.fx.verify(); self.assertEqual("FAIL",r["status"]); self.assertEqual(1,r["scan"]["rule_counts"].get("VARCH-012"))
        self.fx.write(path,old); manifest_path.write_text(old_manifest); self.fx.dep=old_dep; self.fx.assert_pass(self)

    def test_domain_cross_feature_import_fails_and_recovers(self):
        path="feature/a/src/main/kotlin/com/verto/app/feature/a/domain/APublic.kt"; old=(self.fx.root/path).read_text()
        self.fx.write(path,"package com.verto.app.feature.a.domain\nimport com.verto.app.feature.b.domain.BPublic\nclass APublic\n")
        r=self.fx.verify(); self.assertEqual("FAIL",r["status"]); self.assertEqual(1,r["scan"]["rule_counts"].get("VARCH-003")); self.recover(path,old)

    def test_removed_legacy_identity_reintroduction_fails_and_recovers(self):
        path="feature/a/src/main/kotlin/com/verto/app/feature/a/application/AService.kt"; old=(self.fx.root/path).read_text()
        bad="package com.verto.app.feature.a.application\nimport com.verto.app.data.local.dao.SeedDao\nclass AService\n"
        self.fx.write(path,bad); first=self.fx.verify(); legacy=next(v for v in first["scan"]["violations"] if v["rule_id"]=="VARCH-005")
        self.fx.debt["architecture"]["violations"]=[legacy]; self.fx.governance["legacy_architecture_identities"]=[legacy["identity"]]
        self.fx.write(path,old); self.fx.write(guard.DEFAULT_FORWARD_DEBT, json.dumps({"admitted_legacy_architecture_identities":[]})); self.fx.assert_pass(self)
        self.fx.write(path,bad); self.fail_code("FAIL_REMOVED_DEBT_REAPPEARED")
        self.fx.write(path,old); self.fx.assert_pass(self)

    def test_dependency_cycle_fails_and_recovers(self):
        a="feature/a/build.gradle.kts"; b="feature/b/build.gradle.kts"; oa=(self.fx.root/a).read_text(); ob=(self.fx.root/b).read_text()
        self.fx.write(a,'dependencies {\n implementation(project(":feature:b"))\n}\n'); self.fx.write(b,'dependencies {\n implementation(project(":feature:a"))\n}\n')
        r=self.fx.verify(); self.assertEqual("FAIL",r["status"]); self.assertGreater(r["scan"]["metrics"]["dependency_cycles"],0)
        self.fx.write(a,oa); self.fx.write(b,ob); self.fx.assert_pass(self)

    def test_baseline_loosening_fails_and_recovers(self):
        self.fx.governance["kotlin_quality_metrics"]={"broad_catches":2}; self.fx.debt["kotlin_quality"]["metrics"]={"broad_catches":3}
        self.fail_code("FAIL_BASELINE_LOOSENING"); self.fx.debt["kotlin_quality"]["metrics"]={"broad_catches":2}; self.fx.assert_pass(self)

    def test_severity_downgrade_fails_and_recovers(self):
        original=self.fx.contract["rules"][0]["severity"]; self.fx.contract["rules"][0]["severity"]="WARNING"; self.fail_code("FAIL_SEVERITY_DOWNGRADE")
        self.fx.contract["rules"][0]["severity"]=original; self.fx.assert_pass(self)

    def test_malformed_exception_fails_and_recovers(self):
        self.fx.exceptions["exceptions"]=[{"rule_id":"VARCH-001"}]; self.fail_code("FAIL_EXCEPTION_GOVERNANCE")
        self.fx.exceptions["exceptions"]=[]; self.fx.assert_pass(self)

    def test_complexity_regression_fails_and_recovers(self):
        path="feature/a/src/main/kotlin/com/verto/app/feature/a/application/Complex.kt"
        self.fx.write(path,"package com.verto.app.feature.a.application\nfun compute(x:Int):Int { return x }\n")
        baseline=complexity.make_baseline(self.fx.root,"fixture","0"*64)
        self.assertEqual("PASS",complexity.verify(self.fx.root,baseline)["status"])
        self.fx.write(path,"package com.verto.app.feature.a.application\nfun compute(x:Int):Int { if(x>0){ if(x>1){ return 2 } }; return x }\n")
        self.assertEqual("FAIL",complexity.verify(self.fx.root,baseline)["status"])
        self.fx.write(path,"package com.verto.app.feature.a.application\nfun compute(x:Int):Int { return x }\n")
        self.assertEqual("PASS",complexity.verify(self.fx.root,baseline)["status"])

    def test_change_contract_scope_fails_and_recovers(self):
        base=change.snapshot(self.fx.root); bp=self.fx.root/"baseline.json"; bp.write_text(json.dumps(base))
        cp=self.fx.root/"contract.json"; cp.write_text(json.dumps({
            "session":{"id":1,"input_sha256":"0"*64,"baseline_snapshot":"baseline.json","scope":"fixture","allowed_modules":[],"allowed_files":["docs/**"],"forbidden_files":["**/src/main/**/*.kt"]},
            "architecture":{"invariants":[],"allowed_new_dependencies":[],"forbidden_dependencies":[]},"verification":{"required_tests":[],"required_gates":[]}
        }))
        # baseline/contract are newly added, so allow their own paths for this isolated test.
        data=json.loads(cp.read_text()); data["session"]["allowed_files"] += ["baseline.json","contract.json"]; cp.write_text(json.dumps(data))
        # Re-baseline after adding governance files so only the production mutation is tested.
        bp.write_text(json.dumps(change.snapshot(self.fx.root)))
        target=self.fx.root/"feature/a/src/main/kotlin/com/verto/app/feature/a/domain/APublic.kt"; old=target.read_text(); target.write_text(old+"\n")
        r=change.verify(self.fx.root,cp); self.assertEqual("FAIL",r["status"]); self.assertTrue(any(x.get("code")=="FAIL_CHANGE_CONTRACT_SCOPE" for x in r["failures"]))
        target.write_text(old); self.assertEqual("PASS",change.verify(self.fx.root,cp)["status"])

if __name__ == "__main__": unittest.main(verbosity=2)
