"""MMRL R36 named-flavor hosted-only source contract; does not execute Gradle."""
from __future__ import annotations

import ast
import importlib.util
from pathlib import Path
import subprocess
import sys
import tomllib

import pytest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/mmrl_r36_hosted.py'


def module():
    spec = importlib.util.spec_from_file_location('mmrl_r36_hosted', SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def test_reviewed_flavor_native_graph_and_exact_output():
    mod = module()
    graph = mod.checked_graph(ROOT)
    job = graph['jobs'][0]
    assert job['id'] == 'node-01'
    assert job['android_review']['semantic_request'] is True
    assert job['android_review']['variant'] == 'officialDebug'
    assert job['android_review']['tasks'] == [':app:lintOfficialDebug', ':app:assembleOfficialDebug']
    assert job['android_review']['wrapper_jar_sha256'] == 'd3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd'
    assert job['outputs'] == [mod.OUTPUT]
    assert job['android_review']['release_qualified'] is False
    assert job['android_review']['signer_authority'] is False


def test_hosted_output_is_opt_in_and_release_untouched():
    gradle = (ROOT / 'app/build.gradle.kts').read_text()
    assert 'providers.gradleProperty("mmrlR36HostedDebug")' in gradle
    assert 'variant.name == "officialDebug"' in gradle
    assert 'MMRL-R36-OfficialDebug.apk' in gradle
    assert 'output.versionName.map { vName ->' in gradle
    assert 'releaseSigningProperties = project.releaseSigningProperties()' in gradle
    assert 'refusing to create an unsigned or debug-signed release artifact' in gradle
    cfg = tomllib.loads((ROOT / '.devtool.toml').read_text())
    assert cfg['workspace']['default_target'] == 'mmrl_android'
    assert cfg['targets']['mmrl_android']['android']['release']['verify_signature'] is True
    assert cfg['targets']['mmrl_hosted_r36']['jobs']['official_debug']['outputs'] == [module().OUTPUT]


def test_runner_refuses_local_gradle_even_with_paths(monkeypatch, tmp_path):
    mod = module()
    monkeypatch.delenv('GITHUB_ACTIONS', raising=False)
    assert mod.main(['run','--root',str(ROOT),'--inputs',str(tmp_path/'in'),'--outputs',str(tmp_path/'out')]) == 2
    assert not (tmp_path/'out').exists()
    assert mod.main(['verify','--root',str(ROOT),'--inputs',str(tmp_path/'in'),'--outputs',str(tmp_path/'out'), '--evidence',str(tmp_path/'receipt.json')]) == 2
    assert not (tmp_path/'receipt.json').exists()


def test_hosted_runner_refuses_wrong_source_and_event(monkeypatch):
    mod = module()
    for k,v in dict(GITHUB_ACTIONS='true',RUNNER_OS='Linux',RUNNER_ARCH='X64',
                    GITHUB_REPOSITORY='Mikeyphw/MMRL',GITHUB_EVENT_NAME='workflow_dispatch',
                    ORG_GRADLE_PROJECT_mmrlR36HostedDebug='true', GITHUB_SHA='f'*40,
                    GITHUB_RUN_ID='1',GITHUB_RUN_ATTEMPT='1').items():
        monkeypatch.setenv(k,v)
    with pytest.raises(ValueError,match='exact GitHub'):
        mod.check_runner(ROOT)
    monkeypatch.setenv('GITHUB_SHA',subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip())
    monkeypatch.setenv('GITHUB_EVENT_NAME','push')
    with pytest.raises(ValueError,match='exact GitHub'):
        mod.check_runner(ROOT)


def test_workflow_only_native_devtool_commands_and_no_release_authority():
    import yaml
    workflow = (ROOT/'.github/workflows/devtool-mmrl-r36-hosted-official-debug.yml').read_text()
    data = yaml.safe_load(workflow)
    assert data['permissions'] == {'contents':'read'}
    assert data['jobs']['official-debug']['env']['ORG_GRADLE_PROJECT_mmrlR36HostedDebug']=='true'
    assert ':app:assembleOfficialRelease' not in workflow
    assert 'secrets.' not in workflow
    assert 'run_node' not in workflow or 'mmrl_r36_hosted.py run' in workflow
    assert 'mmrl_r36_hosted.py run' in workflow
    assert 'mmrl_r36_hosted.py verify' in workflow
    assert 'git clone --no-checkout https://github.com/Mikeyphw/devtool.git' in workflow
    assert './gradlew' not in workflow
    assert 'ndk;29.0.14206865' in workflow
    ast.parse(SCRIPT.read_text())


def test_existing_workflows_preserved():
    for name in ('devtool-android-build.yml','mmrl-release-seal.yml'):
        assert (ROOT/'.github/workflows'/name).is_file()
    assert 'assembleOfficialRelease' in (ROOT/'.github/workflows/mmrl-release-seal.yml').read_text()
