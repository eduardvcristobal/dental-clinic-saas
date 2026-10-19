package com.dentalapp.domain.entity;

import com.dentalapp.common.*;
import com.dentalapp.domain.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "file_asset")
@Getter @Setter
public class FileAsset extends TenantEntity {
    @ManyToOne(fetch = FetchType.LAZY) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY) private Visit visit;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FileKind kind;
    /** Key in S3-compatible object storage. Prefix keys with the clinic id. */
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "mime_type") private String mimeType;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(name = "uploaded_by") private UUID uploadedBy;
}
