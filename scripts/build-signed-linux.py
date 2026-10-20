#!/usr/bin/env python3
"""Build with private signing configuration, never passwords in arguments or logs."""
import argparse, json, os, pathlib, subprocess
p=argparse.ArgumentParser();p.add_argument('--credentials',required=True);p.add_argument('--java-home',required=True);p.add_argument('--sdk',required=True);p.add_argument('--gradle-cache');a=p.parse_args()
root=pathlib.Path(__file__).resolve().parent.parent
if not (root/'app/google-services.json').is_file():raise SystemExit('Restore the existing Firebase configuration before a production build')
config=json.loads(pathlib.Path(a.credentials).read_text());env=os.environ.copy();env.update({'JAVA_HOME':a.java_home,'ANDROID_HOME':a.sdk,'PATH':str(pathlib.Path(a.java_home)/'bin')+os.pathsep+env['PATH'],'RBW_ANDROID_KEYSTORE':config['keystore'],'RBW_ANDROID_KEYSTORE_PASSWORD':config['storePassword'],'RBW_ANDROID_KEY_ALIAS':config['alias'],'RBW_ANDROID_KEY_PASSWORD':config['keyPassword']})
if a.gradle_cache:env['GRADLE_USER_HOME']=a.gradle_cache
os.umask(0o077)
subprocess.run(['bash','gradlew',':app:testDebugUnitTest',':app:lintRelease',':app:assembleRelease','--no-daemon'],cwd=root,env=env,check=True)
