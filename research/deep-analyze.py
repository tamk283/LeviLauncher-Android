import json, pathlib, re, bisect, subprocess, sys, hashlib
from elftools.elf.elffile import ELFFile
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN

lib=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2]); out.mkdir(exist_ok=True)
report={'analysis':'static only; no APK/game code execution','verified_hooks':[], 'runtime_validation':False}
with lib.open('rb') as f:
    elf=ELFFile(f)
    report['elf_sections']=[{'name':s.name,'address':hex(s['sh_addr']),'size':s['sh_size']} for s in elf.iter_sections() if s.name in ['.text','.rodata','.dynsym','.symtab','.eh_frame','.data.rel.ro']]
    dyn=elf.get_section_by_name('.dynsym'); symbols=list(dyn.iter_symbols()) if dyn else []
    names={s.name:s for s in symbols}; cpp=[s.name for s in symbols if s.name.startswith('_Z')]
    report['cpp_dynamic_symbol_count']=len(cpp); report['cpp_dynamic_preview']=cpp[:25]
    report['jni_exports']=[s.name for s in symbols if s.name.startswith('Java_')][:80]
    candidates=json.loads(pathlib.Path(sys.argv[3]).read_text())['signatures']
    report['reference_symbol_checks']={k:[{'symbol':c['symbol'],'present':c['symbol'] in names} for c in v['candidates'] if 'symbol' in c] for k,v in candidates.items()}
    terms=['LocalPlayer','ClientInstance','LevelRendererPlayer','LevelRenderer','ActorRenderDispatcher','CameraComponent','CameraSystem','StateVectorComponent','ActorRuntimeID','ActorUniqueID','PlayerRender','MinecraftGame','worldToScreen','getPosition','getNameTag','renderActor','actor_render','render_name_tags']
    anchors=[]; counts={t:0 for t in terms}
    for sec in elf.iter_sections():
        if sec.name not in ['.rodata','.dynstr','.data.rel.ro']:continue
        data=sec.data()
        for term in terms:
            needle=term.encode();pos=0
            while True:
                pos=data.find(needle,pos)
                if pos<0:break
                counts[term]+=1
                if counts[term]<=25:
                    start=pos
                    while start>max(0,pos-140) and 32<=data[start-1]<127:start-=1
                    end=pos+len(needle)
                    while end<min(len(data),pos+200) and 32<=data[end]<127:end+=1
                    anchors.append({'term':term,'section':sec.name,'address':hex(sec['sh_addr']+start),'match_address':hex(sec['sh_addr']+pos),'string':data[start:end].decode('ascii','replace')})
                pos+=len(needle)
    report['anchor_counts']=counts; report['anchor_samples']=anchors[:400]
    text=elf.get_section_by_name('.text')
    targets={int(a['address'],16) for a in anchors}|{int(a['match_address'],16) for a in anchors}
    (out/'targets.txt').write_text('\n'.join(hex(t) for t in sorted(targets)))
    result=subprocess.run([sys.argv[4],str(lib),str(text['sh_offset']),str(text['sh_addr']),str(text['sh_size']),str(out/'targets.txt')],capture_output=True,text=True,timeout=180)
    if result.returncode:raise RuntimeError('Cross-reference scanner failed: '+result.stderr[:1000])
    xrefs=[json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    report['adrp_add_candidate_count']=len(xrefs)
    lookup={}
    for a in anchors:
        for key in [int(a['address'],16),int(a['match_address'],16)]:lookup.setdefault(key,[]).append(a['term'])
    dis=Cs(CS_ARCH_ARM64,CS_MODE_LITTLE_ENDIAN)
    windows=[]
    for ref in xrefs[:160]:
        pc=ref['pc'];start=max(text['sh_addr'],pc-24);size=80
        f.seek(text['sh_offset']+start-text['sh_addr']);code=f.read(size)
        window={'xref':hex(pc),'target':hex(ref['target']),'terms':sorted(set(lookup.get(ref['target'],[]))),'instructions':[hex(ins.address)+' '+ins.mnemonic+' '+ins.op_str for ins in dis.disasm(code,start)]}
        windows.append(window)
    report['xref_disassembly_samples']=windows[:50]
    (out/'xref-windows.json').write_text(json.dumps(windows,indent=2))
    report['limitations']=['ADRP+ADD pairs are static reference candidates, not verified callable game functions.','Class-name strings or RTTI do not establish player fields, projection matrices or function ABI.','No offsets are approved for injection; an on-device diagnostic test is still needed.','Only a bounded sample of each anchor is scanned; absence of candidates is not proof that a function is absent.']
    report['status']='static deep analysis completed'
(out/'deep-report.json').write_text(json.dumps(report,indent=2))
print(json.dumps({'status':report['status'],'cpp_dynamic_symbol_count':report['cpp_dynamic_symbol_count'],'anchor_counts':counts,'xref_candidates':len(xrefs)},indent=2))
