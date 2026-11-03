#!/usr/bin/env python3
"""Offline R8 recovery and artifact-reuse unit tests (no ADB/Gradle calls)."""
from pathlib import Path
import importlib.util
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
SCRIPTS=ROOT/'scripts'
import sys
sys.path.insert(0, str(SCRIPTS))
spec=importlib.util.spec_from_file_location('mmrl_phase06_lab_write', SCRIPTS/'mmrl_phase06_lab_write.py')
r8=importlib.util.module_from_spec(spec)
spec.loader.exec_module(r8)


class R8Logic(unittest.TestCase):
    def journal(self, original):
        return {
            'schema':'mmrl.lsposed.phase06.lab-write-host.v1',
            'serial':'localhost:5555','original_enabled':False,
            'original_scope':original,'module_package':r8.LAB_MODULE,
            'target_package':r8.LAB_TARGET,'phase':'PRE_WRITE_JOURNALED'
        }

    def recover_with_state(self, original, active, scoped):
        state={'enabled':'enabled' if active else 'disabled','scopes':list(scoped)}
        actions=[]
        def cli(serial,*cmd):
            actions.append(cmd)
            if cmd==('modules','disable',r8.LAB_MODULE):
                state['enabled']='disabled'
            elif cmd==('scope','rm',r8.LAB_MODULE,r8.EXPECTED_SCOPE):
                state['scopes']=[]
            elif cmd==('scope','add',r8.LAB_MODULE,r8.EXPECTED_SCOPE):
                state['scopes']=[r8.EXPECTED_SCOPE]
                # Vector v2.2 implicitly enables modules when a scope is added.
                state['enabled']='enabled'
            else:
                raise AssertionError('Unsafe recovery command: '+str(cmd))
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'journal.json'
            doc=self.journal(original)
            r8.persist(path,doc,create=True)
            with patch.object(r8.lab,'modules',side_effect=lambda s:state['enabled']), \
                 patch.object(r8.lab,'scope',side_effect=lambda s:state['scopes']), \
                 patch.object(r8.lab,'cli',side_effect=cli):
                r8.recover('localhost:5555',path,doc)
            self.assertTrue(doc['restored'])
            self.assertEqual(doc['phase'],'HOST_RESTORED')
            self.assertEqual(state['scopes'],original)
            self.assertEqual(state['enabled'],'disabled')
            return actions

    def test_recover_disable_then_remove_lab_target_only(self):
        self.assertEqual(self.recover_with_state([],True,[r8.EXPECTED_SCOPE]),[
            ('scope','rm',r8.LAB_MODULE,r8.EXPECTED_SCOPE),
            ('modules','disable',r8.LAB_MODULE)])

    def test_recover_disable_then_add_lab_target_only(self):
        self.assertEqual(self.recover_with_state([r8.EXPECTED_SCOPE],True,[]),[
            ('scope','add',r8.LAB_MODULE,r8.EXPECTED_SCOPE),
            ('modules','disable',r8.LAB_MODULE)])

    def test_recover_scope_add_auto_enable_even_from_disabled(self):
        self.assertEqual(self.recover_with_state([r8.EXPECTED_SCOPE],False,[]),[
            ('scope','add',r8.LAB_MODULE,r8.EXPECTED_SCOPE),
            ('modules','disable',r8.LAB_MODULE)])

    def test_recover_noop_when_baseline_unchanged(self):
        self.assertEqual(self.recover_with_state([],False,[]),[])

    def test_recover_refuses_unknown_scope_without_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'journal.json'
            doc=self.journal([])
            r8.persist(path,doc,create=True)
            with patch.object(r8.lab,'modules',return_value='enabled'), \
                 patch.object(r8.lab,'scope',return_value=['com.unrelated.app/0']), \
                 patch.object(r8.lab,'cli') as command:
                with self.assertRaisesRegex(RuntimeError, 'Lab state or scope is unexpected; refusing unsafe restoration'):
                    r8.recover('localhost:5555',path,doc)
                command.assert_not_called()

    def test_recover_refuses_non_lab_journal(self):
        with tempfile.TemporaryDirectory() as td:
            doc=self.journal([])
            doc['module_package']='com.someone.else'
            with patch.object(r8.lab,'cli') as command:
                with self.assertRaisesRegex(RuntimeError,'does not authorize'):
                    r8.recover('localhost:5555',Path(td)/'journal.json',doc)
                command.assert_not_called()

    def test_missing_lab_package_does_not_query_pm_path(self):
        with patch.object(r8, 'adb', return_value='') as adb_call, \
             patch.object(r8, 'checked') as pull:
            self.assertFalse(r8.installed_apk('localhost:5555', r8.LAB_MODULE, Path('/not-created')))
            adb_call.assert_called_once_with(
                'localhost:5555', 'shell', 'pm', 'list', 'packages', '--user', '0', r8.LAB_MODULE)
            pull.assert_not_called()

    def test_package_filter_must_match_exact_name(self):
        with patch.object(r8, 'adb', return_value='package:' + r8.LAB_MODULE + '.other') as adb_call:
            self.assertFalse(r8.installed_apk('localhost:5555', r8.LAB_MODULE, Path('/not-created')))
            self.assertEqual(adb_call.call_count, 1)

    def test_listed_package_missing_path_fails_closed(self):
        def call(serial, *args):
            if 'list' in args:
                return 'package:' + r8.LAB_MODULE
            if 'path' in args:
                return ''
            raise AssertionError('Unexpected ADB command: ' + str(args))
        with patch.object(r8, 'adb', side_effect=call), \
             patch.object(r8, 'checked') as pull:
            with self.assertRaisesRegex(RuntimeError, 'Cannot hash exact single installed APK'):
                r8.installed_apk('localhost:5555', r8.LAB_MODULE, Path('/not-created'))
            pull.assert_not_called()

    def test_installed_package_requires_single_hashable_apk(self):
        with tempfile.TemporaryDirectory() as td:
            destination = Path(td) / 'base.apk'
            def call(serial, *args):
                if 'list' in args:
                    return 'package:' + r8.LAB_MODULE
                if 'path' in args:
                    return 'package:/data/app/synthetic/base.apk'
                raise AssertionError('Unexpected ADB command: ' + str(args))
            def pull(*args, **kwargs):
                self.assertEqual(args[:3], ('adb', '-s', 'localhost:5555'))
                self.assertEqual(args[3], 'pull')
                destination.write_bytes(b'synthetic APK bytes')
                return ''
            with patch.object(r8, 'adb', side_effect=call), \
                 patch.object(r8, 'checked', side_effect=pull):
                self.assertTrue(r8.installed_apk('localhost:5555', r8.LAB_MODULE, destination))

    def test_package_listing_error_not_treated_as_absence(self):
        with patch.object(r8, 'adb', side_effect=RuntimeError('ADB unavailable')):
            with self.assertRaisesRegex(RuntimeError, 'ADB unavailable'):
                r8.installed_apk('localhost:5555', r8.LAB_MODULE, Path('/not-created'))

    def test_existing_apk_mismatch_refuses_before_any_lab_install(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            expected={
                'com.dergoogler.mmrl.phase06.r1a1':root/'app.apk',
                'com.dergoogler.mmrl.phase06.r1a1.test':root/'test.apk',
                r8.LAB_TARGET:root/'target.apk', r8.LAB_MODULE:root/'module.apk'
            }
            for p in expected.values():p.write_bytes(b'expected')
            def installed(serial,pkg,path):
                if pkg==r8.LAB_TARGET:return False
                path.write_bytes(b'BAD' if pkg==r8.LAB_MODULE else b'expected')
                return True
            with patch.object(r8,'installed_apk',side_effect=installed), \
                 patch.object(r8,'adb') as adb_command:
                with self.assertRaisesRegex(RuntimeError,'Installed APK differs'):
                    r8.ensure_pair('localhost:5555',expected,root,install_lab=True)
                adb_command.assert_not_called()

if __name__=='__main__':unittest.main(verbosity=2)
