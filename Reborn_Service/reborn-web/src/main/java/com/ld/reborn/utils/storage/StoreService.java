package com.ld.reborn.utils.storage;

import com.ld.reborn.vo.FileVO;

import java.util.List;

/**
 * 储存服务
 */
public interface StoreService {

    void deleteFile(List<String> files);

    FileVO saveFile(FileVO fileVO);

    String getStoreName();
}
