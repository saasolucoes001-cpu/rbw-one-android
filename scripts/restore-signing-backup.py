#!/usr/bin/env python3
"""Restore an authenticated Android signing backup into a new private directory."""
import argparse,base64,getpass,io,json,os,pathlib,tarfile
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.serialization.pkcs12 import load_key_and_certificates
from cryptography.hazmat.primitives import hashes
p=argparse.ArgumentParser();p.add_argument('backup');p.add_argument('destination');a=p.parse_args()
os.umask(0o077);destination=pathlib.Path(a.destination)
if destination.exists():raise SystemExit('Destination must be new; existing signing material will not be overwritten')
data=json.loads(pathlib.Path(a.backup).read_bytes())
if data.get('format')!='rbw-android-signing-backup' or data.get('version')!=1 or data.get('cipher')!='AES-256-GCM' or data.get('kdf')!='scrypt' or (data.get('n'),data.get('r'),data.get('p'))!=(2**17,8,1):raise SystemExit('Unsupported backup format')
code=getpass.getpass('Código de recuperação: ');ciphertext=base64.b64decode(data.pop('ciphertext'))
key=Scrypt(salt=base64.b64decode(data['salt']),length=32,n=data['n'],r=data['r'],p=data['p']).derive(code.strip().encode())
plain=AESGCM(key).decrypt(base64.b64decode(data['nonce']),ciphertext,json.dumps(data,sort_keys=True).encode())
allowed={'rbw-one-android.p12','credentials.json','certificate.pem','google-services.json'};files={}
with tarfile.open(fileobj=io.BytesIO(plain),mode='r:gz') as tar:
 for m in tar.getmembers():
  if m.name not in allowed or not m.isfile() or m.size>1_000_000 or m.name in files:raise SystemExit('Invalid backup member')
  files[m.name]=tar.extractfile(m).read()
if not {'rbw-one-android.p12','credentials.json','certificate.pem'}.issubset(files):raise SystemExit('Incomplete backup')
credentials=json.loads(files['credentials.json']);private,cert,_=load_key_and_certificates(files['rbw-one-android.p12'],credentials['storePassword'].encode())
if not private or cert is None:raise SystemExit('Invalid signing key')
destination.mkdir(mode=0o700,parents=True);destination.chmod(0o700)
credentials['keystore']=str((destination/'rbw-one-android.p12').resolve());files['credentials.json']=(json.dumps(credentials,indent=2)+'\n').encode()
for name,payload in files.items():
 target=destination/name;fd=os.open(target,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as f:f.write(payload)
print('Restored signing material. Certificate SHA-256: '+cert.fingerprint(hashes.SHA256()).hex())
