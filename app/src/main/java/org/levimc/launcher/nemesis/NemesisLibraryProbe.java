package org.levimc.launcher.nemesis;

import java.io.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;

/** File-only diagnostics; never accesses process memory or installs hooks. */
public final class NemesisLibraryProbe {
    public static final String TARGET_VERSION = "1.26.60.4";
    public static final String EXPECTED_BUILD_ID = "eb291b883dfdfaff70c4999c2eabd61885ae3e15";
    public static final String EXPECTED_SHA256 = "4e7f97ecf1989edfaa7ca4805acc907a63bd4c56f1b1965079b2c47f63e3a7c4";
    private NemesisLibraryProbe() { }
    public static final class Result {
        public String status = "unavailable", architecture = "", buildId = "", sha256 = "";
        public boolean loaded, fileMatches;
        public long fileSize;
    }
    public static Result probe(boolean inGame) {
        Result result = new Result();
        if (!inGame) { result.status = "not_in_game"; return result; }
        try {
            Set<String> paths = new LinkedHashSet<>();
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
                String line; int count = 0;
                while ((line = reader.readLine()) != null && count++ < 32768) {
                    String[] fields = line.trim().split("\\s+", 6);
                    if (fields.length != 6 || !fields[5].contains("libminecraftpe.so")) continue;
                    if (fields[5].endsWith(" (deleted)")) { result.status = "deleted_mapping"; return result; }
                    if (fields[5].startsWith("/") && fields[5].endsWith("/libminecraftpe.so")) paths.add(fields[5]);
                }
            }
            if (paths.isEmpty()) { result.status = "not_found"; return result; }
            if (paths.size() != 1) { result.status = "ambiguous"; return result; }
            result = inspectFile(new File(paths.iterator().next())); result.loaded = true;
        } catch (Exception error) { result.status = "read_error"; }
        return result;
    }
    static Result inspectFile(File file) throws Exception {
        Result result = new Result();
        if (!file.isFile() || !file.canRead()) { result.status = "unreadable"; return result; }
        result.fileSize = file.length();
        if (result.fileSize < 64 || result.fileSize > 2L*1024*1024*1024) { result.status = "invalid_elf"; return result; }
        try (RandomAccessFile input = new RandomAccessFile(file,"r")) {
            byte[] header = new byte[64]; input.readFully(header);
            if (header[0]!=0x7f || header[1]!='E' || header[2]!='L' || header[3]!='F' || header[4]!=2 || header[5]!=1) {
                result.status="invalid_elf"; return result;
            }
            ByteBuffer elf=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if (Short.toUnsignedInt(elf.getShort(18))!=183) { result.status="wrong_architecture"; return result; }
            result.architecture="ARM64 / AArch64";
            long table=elf.getLong(32); int entry=Short.toUnsignedInt(elf.getShort(54)),count=Short.toUnsignedInt(elf.getShort(56));
            if (table<64 || table>result.fileSize || entry<56 || entry>256 || count<1 || count>512 || (long)entry*count>result.fileSize-table) {
                result.status="invalid_elf"; return result;
            }
            for(int i=0;i<count;i++) {
                input.seek(table+(long)i*entry); byte[] ph=new byte[56];input.readFully(ph);
                ByteBuffer program=ByteBuffer.wrap(ph).order(ByteOrder.LITTLE_ENDIAN);
                if(program.getInt(0)!=4)continue;
                long offset=program.getLong(8),size=program.getLong(32);
                if(offset<0 || offset>result.fileSize || size<12 || size>1024*1024 || size>result.fileSize-offset)continue;
                byte[] notes=new byte[(int)size];input.seek(offset);input.readFully(notes);
                ByteBuffer note=ByteBuffer.wrap(notes).order(ByteOrder.LITTLE_ENDIAN);
                for(int p=0;p<=notes.length-12;) {
                    int names=note.getInt(p),desc=note.getInt(p+4),type=note.getInt(p+8);
                    if(names<0 || desc<0 || names>notes.length || desc>notes.length)break;
                    long start=p+12L+((names+3L)&~3L),end=start+((desc+3L)&~3L);
                    if(end>notes.length)break;
                    if(type==3 && names>=3 && desc>=12 && desc<=64 && notes[p+12]=='G' && notes[p+13]=='N' && notes[p+14]=='U') {
                        result.buildId=hex(Arrays.copyOfRange(notes,(int)start,(int)start+desc));break;
                    }
                    p=(int)end;
                }
                if(!result.buildId.isEmpty())break;
            }
        }
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream input=new FileInputStream(file)) {
            byte[] buffer=new byte[1024*1024];int size;
            while((size=input.read(buffer))!=-1) {
                if(Thread.currentThread().isInterrupted()){result.status="cancelled";return result;}
                digest.update(buffer,0,size);
            }
        }
        result.sha256=hex(digest.digest());
        result.fileMatches=EXPECTED_BUILD_ID.equals(result.buildId)&&EXPECTED_SHA256.equals(result.sha256);
        result.status=result.fileMatches?"file_match":"different_binary";
        return result;
    }
    private static String hex(byte[] bytes) {
        char[] alphabet="0123456789abcdef".toCharArray(),output=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){output[i*2]=alphabet[(bytes[i]>>>4)&15];output[i*2+1]=alphabet[bytes[i]&15];}
        return new String(output);
    }
}
