package com.company.vapt.ingestion;

import com.company.vapt.finding.entity.Finding;

import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.ingestion.entity.SourceFile;
import com.company.vapt.ingestion.repository.SourceFileRepository;
import com.company.vapt.ingestion.storage.FileStorageService;
import com.company.vapt.ingestion.storage.LocalDiskFileStorageService;
import com.company.vapt.ingestion.storage.StorageException;
import com.company.vapt.ingestion.storage.StorageProperties;
import com.company.vapt.ingestion.storage.StoredFile;
import com.company.vapt.report.domain.ReportSectionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 3 Task 3 - Immutable Local File Storage Service Tests")
class FileStorageServiceTest {

    @Autowired
    private SourceFileRepository sourceFileRepository;

    @TempDir
    Path tempStorageDir;

    private FileStorageService storageService;
    private StorageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setRootDir(tempStorageDir.toString());
        storageService = new LocalDiskFileStorageService(properties);
    }

    @Test
    @DisplayName("1, 2, 3. Successful file storage, correct SHA-256 checksum and correct file size")
    void testSuccessfulStorageAndChecksum() throws Exception {
        byte[] csvContent = "Targets,Vulnerability,Severity\n10.20.30.40,Open SSL,High\n".getBytes(StandardCharsets.UTF_8);
        String expectedChecksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(csvContent)
        );

        StoredFile stored = storageService.store(
                ReportSectionId.GDP_VA_REPORT,
                new ByteArrayInputStream(csvContent),
                "weekly-gdp-report.csv",
                "text/csv",
                "manohar.kaloni@transunion.com"
        );

        assertNotNull(stored.fileId());
        assertEquals("weekly-gdp-report.csv", stored.originalFilename());
        assertEquals(".csv", stored.fileExtension());
        assertEquals(csvContent.length, stored.fileSizeBytes());
        assertEquals(expectedChecksum, stored.sha256Checksum());
        assertEquals("manohar.kaloni@transunion.com", stored.uploadedBy());
        assertTrue(storageService.exists(stored.storagePath()));

        // Verify stored file content matches byte-for-byte
        try (InputStream is = storageService.openStream(stored.storagePath())) {
            byte[] readBytes = is.readAllBytes();
            assertArrayEquals(csvContent, readBytes);
        }
    }

    @Test
    @DisplayName("4 & 5. Unique generated storage names and overwrite prevention")
    void testUniqueStorageNamesAndOverwritePrevention() {
        byte[] content = "IP,Host\n127.0.0.1,localhost\n".getBytes(StandardCharsets.UTF_8);

        StoredFile stored1 = storageService.store(
                ReportSectionId.WIZ,
                new ByteArrayInputStream(content),
                "scan.csv",
                "text/csv",
                "admin@transunion.com"
        );

        StoredFile stored2 = storageService.store(
                ReportSectionId.WIZ,
                new ByteArrayInputStream(content),
                "scan.csv",
                "text/csv",
                "admin@transunion.com"
        );

        assertNotEquals(stored1.fileId(), stored2.fileId());
        assertNotEquals(stored1.storagePath(), stored2.storagePath());
        assertTrue(storageService.exists(stored1.storagePath()));
        assertTrue(storageService.exists(stored2.storagePath()));
    }

    @Test
    @DisplayName("6. Path traversal rejection in filenames and relative storage paths")
    void testPathTraversalRejection() {
        byte[] content = "test data\n".getBytes(StandardCharsets.UTF_8);

        // Path traversal in filename
        assertThrows(ValidationException.class, () -> {
            storageService.store(
                    ReportSectionId.GDP_VA_REPORT,
                    new ByteArrayInputStream(content),
                    "../../etc/passwd.csv",
                    "text/csv",
                    "admin@transunion.com"
            );
        });

        // Path traversal with backslashes
        assertThrows(ValidationException.class, () -> {
            storageService.store(
                    ReportSectionId.GDP_VA_REPORT,
                    new ByteArrayInputStream(content),
                    "..\\windows\\system32.csv",
                    "text/csv",
                    "admin@transunion.com"
            );
        });

        // Path traversal in resolvePath
        assertThrows(ValidationException.class, () -> {
            storageService.resolvePath("../../outside.dat");
        });
    }

    @Test
    @DisplayName("7. Empty file rejection")
    void testEmptyFileRejection() {
        byte[] emptyContent = new byte[0];

        ValidationException ex = assertThrows(ValidationException.class, () -> {
            storageService.store(
                    ReportSectionId.VERIZON_26_NETWORK_PT,
                    new ByteArrayInputStream(emptyContent),
                    "empty-report.csv",
                    "text/csv",
                    "admin@transunion.com"
            );
        });
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("8. Unsupported extension rejection")
    void testUnsupportedExtensionRejection() {
        byte[] content = "sample content\n".getBytes(StandardCharsets.UTF_8);

        // .exe extension
        assertThrows(ValidationException.class, () -> {
            storageService.store(
                    ReportSectionId.GDP_VA_REPORT,
                    new ByteArrayInputStream(content),
                    "payload.exe",
                    "application/octet-stream",
                    "admin@transunion.com"
            );
        });

        // No extension
        assertThrows(ValidationException.class, () -> {
            storageService.store(
                    ReportSectionId.GDP_VA_REPORT,
                    new ByteArrayInputStream(content),
                    "no-extension-file",
                    "text/plain",
                    "admin@transunion.com"
            );
        });
    }

    @Test
    @DisplayName("9. Invalid content signature rejection (magic bytes mismatch)")
    void testInvalidContentSignatureRejection() {
        // Plain text content disguised with .xlsx extension
        byte[] fakeXlsx = "This is not a real zip or xlsx file\n".getBytes(StandardCharsets.UTF_8);

        ValidationException ex = assertThrows(ValidationException.class, () -> {
            storageService.store(
                    ReportSectionId.GDP_VA_REPORT,
                    new ByteArrayInputStream(fakeXlsx),
                    "disguised.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "admin@transunion.com"
            );
        });
        assertTrue(ex.getMessage().contains("File content does not match"));

        // Valid XLSX zip magic bytes should pass
        byte[] validXlsxHeader = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        StoredFile storedXlsx = storageService.store(
                ReportSectionId.GDP_VA_REPORT,
                new ByteArrayInputStream(validXlsxHeader),
                "valid-archive.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "admin@transunion.com"
        );
        assertNotNull(storedXlsx);
        assertTrue(storageService.exists(storedXlsx.storagePath()));
    }

    @Test
    @DisplayName("10. Partial-file cleanup after simulated I/O stream failure")
    void testPartialFileCleanupOnFailure() {
        // Failing InputStream that throws IOException mid-stream
        InputStream failingStream = new InputStream() {
            private int count = 0;

            @Override
            public int read() throws IOException {
                count++;
                if (count > 50) {
                    throw new IOException("Simulated network stream termination");
                }
                return 'A';
            }
        };

        assertThrows(StorageException.class, () -> {
            storageService.store(
                    ReportSectionId.INTERNAL_VA_BRINQA,
                    failingStream,
                    "interrupted.csv",
                    "text/csv",
                    "admin@transunion.com"
            );
        });

        // Ensure no partial file was left behind in the section directory
        Path brinqaDir = tempStorageDir.resolve("INTERNAL_VA_BRINQA");
        if (Files.exists(brinqaDir)) {
            try (var stream = Files.list(brinqaDir)) {
                assertEquals(0, stream.count(), "Partial file must be deleted upon write failure");
            } catch (IOException e) {
                fail("Failed to inspect directory: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("11. GDP VA and Internal VA storage directory isolation")
    void testGdpAndInternalVaStorageIsolation() {
        byte[] gdpContent = "GDP Finding 1\n".getBytes(StandardCharsets.UTF_8);
        byte[] brinqaContent = "Brinqa Finding 1\n".getBytes(StandardCharsets.UTF_8);

        StoredFile gdpStored = storageService.store(
                ReportSectionId.GDP_VA_REPORT,
                new ByteArrayInputStream(gdpContent),
                "gdp.csv",
                "text/csv",
                "admin@transunion.com"
        );

        StoredFile brinqaStored = storageService.store(
                ReportSectionId.INTERNAL_VA_BRINQA,
                new ByteArrayInputStream(brinqaContent),
                "brinqa.csv",
                "text/csv",
                "admin@transunion.com"
        );

        assertTrue(gdpStored.storagePath().startsWith("GDP_VA_REPORT/"));
        assertTrue(brinqaStored.storagePath().startsWith("INTERNAL_VA_BRINQA/"));
        assertNotEquals(gdpStored.storagePath(), brinqaStored.storagePath());

        Path gdpPath = storageService.resolvePath(gdpStored.storagePath());
        Path brinqaPath = storageService.resolvePath(brinqaStored.storagePath());

        assertEquals(tempStorageDir.resolve("GDP_VA_REPORT").normalize(), gdpPath.getParent());
        assertEquals(tempStorageDir.resolve("INTERNAL_VA_BRINQA").normalize(), brinqaPath.getParent());
    }

    @Test
    @DisplayName("12. Metadata persistence and retrieval with SourceFileRepository")
    void testMetadataPersistenceAndRetrieval() {
        byte[] content = "Record Id,Severity,Title\nAPP-001,High,SQL Injection\n".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = storageService.store(
                ReportSectionId.APPSEC_FINDINGS_PT,
                new ByteArrayInputStream(content),
                "appsec-q4.csv",
                "text/csv",
                "manohar.kaloni@transunion.com"
        );

        // Persist to database via repository
        SourceFile entity = stored.toEntity();
        SourceFile saved = sourceFileRepository.saveAndFlush(entity);

        assertNotNull(saved.getId());
        assertEquals(stored.fileId(), saved.getId());
        assertEquals(stored.sha256Checksum(), saved.getSha256Checksum());

        // Retrieve by checksum
        Optional<SourceFile> loaded = sourceFileRepository.findBySha256Checksum(stored.sha256Checksum());
        assertTrue(loaded.isPresent());
        assertEquals("appsec-q4.csv", loaded.get().getOriginalFilename());
        assertEquals(stored.storagePath(), loaded.get().getStoragePath());
    }
}
