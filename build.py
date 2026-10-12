#!/usr/bin/env python3
"""Build Screen sleep using the Android SDK and JDK, without Gradle dependencies."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.getenv('ANDROID_HOME') or os.getenv('ANDROID_SDK_ROOT'))
    parser.add_argument('--build-tools', default='35.0.0')
    parser.add_argument('--platform', default='android-35')
    parser.add_argument('--keystore', type=Path, help='Private release key; password comes from SCREEN_SLEEP_KEYSTORE_PASSWORD')
    parser.add_argument('--alias', default='screen-sleep', help='Release signing key alias')
    args = parser.parse_args()
    if not args.sdk:
        parser.error('Set ANDROID_HOME or pass --sdk with the Android SDK directory.')
    sdk = Path(args.sdk).expanduser().resolve()
    tools = sdk / 'build-tools' / args.build_tools
    android = sdk / 'platforms' / args.platform / 'android.jar'
    suffix = '.exe' if os.name == 'nt' else ''
    java_home = os.getenv('JAVA_HOME')

    def java_tool(name):
        result = str(Path(java_home) / 'bin' / (name + suffix)) if java_home else shutil.which(name)
        if not result or not Path(result).is_file():
            parser.error(f'{name} not found. Install a JDK and set JAVA_HOME.')
        return result

    java, javac, keytool = [java_tool(name) for name in ('java', 'javac', 'keytool')]
    for required in (android, tools / ('aapt' + suffix), tools / ('zipalign' + suffix), tools / 'lib/d8.jar', tools / 'lib/apksigner.jar'):
        if not required.is_file():
            parser.error(f'Missing Android SDK component: {required}')

    def run(*command):
        subprocess.run([str(part) for part in command], cwd=ROOT, check=True)

    output = ROOT / 'build'
    output.mkdir(exist_ok=True)
    if args.keystore:
        key = args.keystore.expanduser().resolve()
        if not key.is_file():
            parser.error('Release keystore file does not exist.')
        if not os.getenv('SCREEN_SLEEP_KEYSTORE_PASSWORD'):
            parser.error('Set SCREEN_SLEEP_KEYSTORE_PASSWORD for release signing.')
        alias, password = args.alias, 'env:SCREEN_SLEEP_KEYSTORE_PASSWORD'
    else:
        key = output / 'development.p12'
        alias, password = 'androiddebugkey', 'pass:android'
        if not key.exists():
            run(keytool, '-genkeypair', '-keystore', key, '-storetype', 'PKCS12', '-storepass', 'android', '-keypass', 'android',
                '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Android Debug')
        print('Development signing key: build/development.p12 (not the published release key).')

    with tempfile.TemporaryDirectory(prefix='compile-', dir=output) as temporary:
        work = Path(temporary)
        classes, dex = work / 'classes', work / 'dex'
        classes.mkdir(); dex.mkdir()
        run(javac, '-source', '8', '-target', '8', '-classpath', android, '-d', classes, *sorted(ROOT.glob('src/**/*.java')))
        run(java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26', '--lib', android,
            '--output', dex, *sorted(classes.glob('**/*.class')))
        unsigned, aligned = work / 'unsigned.apk', work / 'aligned.apk'
        run(tools / ('aapt' + suffix), 'package', '-f', '-M', ROOT / 'AndroidManifest.xml', '-S', ROOT / 'res', '-I', android, '-F', unsigned)
        with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as apk:
            apk.write(dex / 'classes.dex', 'classes.dex')
        run(tools / ('zipalign' + suffix), '-f', '4', unsigned, aligned)
        apk = output / 'Screen-sleep.apk'
        run(java, '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', alias, '--ks-pass', password, '--out', apk, aligned)
        run(java, '-jar', tools / 'lib/apksigner.jar', 'verify', apk)
    checksum = hashlib.sha256(apk.read_bytes()).hexdigest()
    (output / 'SHA256SUMS.txt').write_text(f'{checksum}  {apk.name}\n', encoding='utf-8')
    print(f'Built and signature-verified: {apk}')


if __name__ == '__main__':
    main()
