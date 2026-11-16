package com.company.vapt.cmdb.controller;

import com.company.vapt.cmdb.service.CmdbManagementService;
import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.logging.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/admin/cmdb")
@PreAuthorize("hasRole('ADMIN')")
public class CmdbController {
 private final CmdbManagementService service;
 public CmdbController(CmdbManagementService service){this.service=service;}
 @GetMapping("/datasets") public ResponseEntity<ApiResponse<List<Map<String,Object>>>> datasets(){return ok(service.listDatasets());}
 @PostMapping(value="/datasets/{datasetId}/uploads",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<ApiResponse<Map<String,Object>>> upload(@PathVariable String datasetId,@RequestParam("file") MultipartFile file,Authentication auth){return created(service.beginUpload(datasetId,file,auth.getName()));}
 @GetMapping("/uploads/{uploadId}") public ResponseEntity<ApiResponse<Map<String,Object>>> status(@PathVariable UUID uploadId){return ok(service.inspectUpload(uploadId));}
 @GetMapping("/uploads/{uploadId}/preview") public ResponseEntity<ApiResponse<Map<String,Object>>> preview(@PathVariable UUID uploadId,@RequestParam int sheetIndex,@RequestParam int headerRow){return ok(service.preview(uploadId,sheetIndex,headerRow));}
 @PostMapping("/uploads/{uploadId}/preview") public ResponseEntity<ApiResponse<Map<String,Object>>> previewMapping(@PathVariable UUID uploadId,@RequestBody PreviewRequest request){return ok(service.preview(uploadId,request.sheetIndex(),request.headerRow(),request.mapping()));}
 @PostMapping("/uploads/{uploadId}/confirm") public ResponseEntity<ApiResponse<Map<String,Object>>> confirm(@PathVariable UUID uploadId,@RequestBody ConfirmRequest request,Authentication auth){return ok(service.confirm(uploadId,request.sheetIndex(),request.headerRow(),request.mapping(),auth.getName()));}
 @GetMapping("/datasets/{datasetId}/versions") public ResponseEntity<ApiResponse<PageResponse<Map<String,Object>>>> history(@PathVariable String datasetId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){Page<Map<String,Object>> result=service.history(datasetId,page,size);return ok(PageResponse.from(result));}
 @GetMapping("/datasets/{datasetId}/active") public ResponseEntity<ApiResponse<Map<String,Object>>> active(@PathVariable String datasetId){return ok(service.active(datasetId));}
 @PutMapping("/datasets/{datasetId}/versions/{versionId}/activate") public ResponseEntity<ApiResponse<Map<String,Object>>> activate(@PathVariable String datasetId,@PathVariable UUID versionId,Authentication auth){return ok(service.activate(datasetId,versionId,auth.getName()));}
 private <T> ResponseEntity<ApiResponse<T>> ok(T data){return ResponseEntity.ok(ApiResponse.ok(data,correlationId()));}
 private <T> ResponseEntity<ApiResponse<T>> created(T data){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data,correlationId()));}
 private String correlationId(){return Optional.ofNullable(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).orElse("unknown");}
 public record ConfirmRequest(int sheetIndex,int headerRow,Map<String,Integer> mapping){}
 public record PreviewRequest(int sheetIndex,int headerRow,Map<String,Integer> mapping){}
}
