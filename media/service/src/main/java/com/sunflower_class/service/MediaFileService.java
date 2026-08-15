package com.sunflower_class.service;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.po.MediaFiles;

public interface MediaFileService {

    public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams,
            QueryMediaParamsDto queryMediaParamsDto);

}
