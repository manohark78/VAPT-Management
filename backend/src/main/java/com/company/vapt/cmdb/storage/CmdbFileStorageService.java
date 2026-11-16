package com.company.vapt.cmdb.storage;

import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.ingestion.storage.StorageProperties;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CmdbFileStorageService {
    private final StorageProperties properties;
    public CmdbFileStorageService(StorageProperties properties) { this.properties=properties; }
    public StoredCmdbFile store(String dataset, MultipartFile file) {
        if(file==null || file.isEmpty()) throw new ValidationException("Uploaded file must not be empty");
        String filename=Objects.toString(file.getOriginalFilename(), "").trim();
        if(filename.isEmpty() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) throw new ValidationException("Invalid upload filename");
        String ext=filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if(!properties.getAllowedExtensions().contains(ext)) throw new ValidationException("Only CSV, XLS, or XLSX files are supported");
        String path=dataset+"/"+UUID.randomUUID()+".dat";
        Path target=resolve(path);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long size=0; byte[] first=new byte[8]; int firstCount=0; byte[] buf=new byte[8192];
            try(InputStream in=file.getInputStream(); OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
                int n; while((n=in.read(buf))!=-1) {
                    size+=n; if(size>properties.getMaxFileSizeBytes()) throw new ValidationException("Upload exceeds the configured maximum file size");
                    if(firstCount<first.length) { int copy=Math.min(n,first.length-firstCount); System.arraycopy(buf,0,first,firstCount,copy); firstCount+=copy; }
                    digest.update(buf,0,n); out.write(buf,0,n);
                }
            }
            if(size==0 || !signature(ext,first,firstCount)) throw new ValidationException("File contents do not match the selected CSV/XLS/XLSX format");
            return new StoredCmdbFile(filename,path,size,HexFormat.of().formatHex(digest.digest()),ext);
        } catch(Exception e) {
            try { Files.deleteIfExists(target); } catch(IOException ignored) { }
            if(e instanceof ValidationException v) throw v;
            throw new IllegalStateException("Could not store CMDB upload",e);
        }
    }
    public Path resolve(String relative) {
        Path root=properties.getRootPath().resolve("cmdb").normalize();
        Path file=root.resolve(relative).normalize();
        if(!file.startsWith(root)) throw new ValidationException("Invalid CMDB storage path");
        return file;
    }
    public void delete(String path) { try { Files.deleteIfExists(resolve(path)); } catch(IOException e) { throw new IllegalStateException("Could not clean up CMDB upload",e); } }
    private boolean signature(String ext,byte[] b,int n) {
        if(ext.equals(".xlsx")) return n>=4 && b[0]=='P' && b[1]=='K';
        if(ext.equals(".xls")) return n>=8 && (b[0]&255)==0xd0 && (b[1]&255)==0xcf && (b[2]&255)==0x11 && (b[3]&255)==0xe0;
        for(int i=0;i<n;i++) if(b[i]==0) return false;
        return n>0;
    }
    public record StoredCmdbFile(String filename,String storagePath,long sizeBytes,String checksum,String extension) { }
}
