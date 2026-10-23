#!/usr/bin/env python3
"""Convert the closed, completed world09 in place, preserving its full pre-edit save."""
import fcntl
import gzip
import hashlib
import io
import json
from pathlib import Path
import time
import zipfile
import prepare_blind_architect_playtest as lab


def main():
    root = Path(__file__).resolve().parents[1]
    world = root/'run-lab/saves/Blind Architect 09 Disconnected Supports'
    evidence = root/'build/blind-architect-evidence/cushioning-20261001'
    status = json.loads((root/'run-lab/lab-bridge/status.json').read_text())
    if status['worldLoaded'] or time.time()*1000-status['heartbeat'] > 5000 or Path(status['checkout']).resolve()!=root:
        raise SystemExit('Conversion requires a fresh idle owning client heartbeat.')
    archive = evidence/'before-world-upgrade.zip'
    if archive.exists():
        raise SystemExit('Upgrade evidence already exists; inspect state instead of retrying.')
    with (world/'session.lock').open('r+b') as lock:
        fcntl.lockf(lock, fcntl.LOCK_EX|fcntl.LOCK_NB)
        stream=io.BytesIO(gzip.decompress((world/'level.dat').read_bytes()))
        assert stream.read(1)==b'\x0a'
        name=lab.string(stream); data=lab.read(stream,10)
        assert stream.read()==b''
        assert data['Data'][1]['LevelName']==(8,'Blind Architect 09 Disconnected Supports')
        files=sorted(p for p in world.rglob('*') if p.is_file())
        before={str(p.relative_to(world)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
        evidence.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as z:
            for p in files:z.write(p,str(p.relative_to(world)))
        with zipfile.ZipFile(archive) as z:
            assert {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()}==before
        assert {str(p.relative_to(world)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}==before
        # Preserve every gameplay field, including old scores, player state and history.
        data['Data'][1]['LevelName']=(8,'Blind Architect Reusable Lab')
        encoded=b'\x0a'+lab.utf(name)+lab.write(10,data)
        temporary=world/'level.dat.workbench-upgrade'
        temporary.write_bytes(gzip.compress(encoded));temporary.replace(world/'level.dat')
        # Old exact-world functions remain dormant for historical inspection. The
        # new load/tick tags select only the reusable namespace and its new objective.
        lab.write_pack(world/'datapacks/blind-architect-lab',case='vibration_lab')
        changed=[str(p.relative_to(world)) for p in world.rglob('*') if p.is_file()
                 and before.get(str(p.relative_to(world)))!=hashlib.sha256(p.read_bytes()).hexdigest()]
        assert all(p=='level.dat' or p.startswith('datapacks/blind-architect-lab/') for p in changed)
        manifest={'world':str(world),'display_name':'Blind Architect Reusable Lab','backup':str(archive),
                  'backup_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'before_files':before,
                  'changed_files':sorted(changed),'copied_gameplay_files':0,'status':'Converted closed save; new view not opened or started'}
        (evidence/'world-upgrade.json').write_text(json.dumps(manifest,indent=2)+'\n')
        (world/'blind-lab-upgrade.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Converted existing world09 in place. Display name: Blind Architect Reusable Lab. No new save created.')


if __name__=='__main__':
    main()
