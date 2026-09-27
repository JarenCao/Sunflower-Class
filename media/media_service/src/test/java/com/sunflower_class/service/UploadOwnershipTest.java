package com.sunflower_class.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sunflower_class.config.MinioConfig;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.impl.MediaFileServiceImpl;
import io.minio.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 相同内容文件跨机构上传时的记录归属回归测试。
 */
class UploadOwnershipTest {

    /**
     * 验证跨机构上传相同文件时创建独立归属记录，不覆盖其他机构的记录。
     */
    @Test
    void duplicateFromAnotherInstitutionCreatesOwnRecord() throws Exception {
        var mapper = mock(MediaFilesMapper.class);
        var minio = mock(MinioClient.class);
        var saver = mock(AddMediaFilesService.class);
        var service = new MediaFileServiceImpl();
        ReflectionTestUtils.setField(service, "mediaFilesMapper", mapper);
        ReflectionTestUtils.setField(service, "minioClient", minio);
        ReflectionTestUtils.setField(service, "minioConfig", new MinioConfig());
        ReflectionTestUtils.setField(service, "addMediaFilesService", saver);
        byte[] bytes = { 1, 2, 3 };
        String md5 = DigestUtils.md5Hex(bytes);
        String ownId = DigestUtils.md5Hex("10:" + md5);
        var old = new MediaFiles();
        old.setId(md5);
        old.setCompanyId(20L);
        when(mapper.selectById(md5)).thenReturn(old);
        var stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(3L);
        when(stat.etag()).thenReturn(md5);
        when(minio.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        var own = new MediaFiles();
        own.setId(ownId);
        own.setCompanyId(10L);
        when(saver.addMediaFiles(eq(10L), eq(ownId), anyString(), anyString(), any())).thenReturn(
            own
        );
        var params = new UploadFileParamsDto();
        params.setFilename("cover.png");
        params.setFileType("20101");
        var result = service.uploadFiles(
            10L,
            new MockMultipartFile("filedata", "cover.png", "image/png", bytes),
            params
        );
        assertEquals(ownId, result.getId());
        verify(mapper, never()).deleteById(md5);
        assertEquals(20L, old.getCompanyId());
        verify(saver).addMediaFiles(eq(10L), eq(ownId), anyString(), contains(ownId), eq(params));
    }
}
