package com.github.forest.service;

import com.github.forest.dto.LabelModel;
import com.github.forest.entity.Article;
import com.github.forest.entity.Tag;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * <p>
 * 标签表  服务类
 * </p>
 *
 * @author sunzy
 * @since 2023-05-29
 */
public interface TagService extends IService<Tag> {

    Tag saveTag(Tag tag);

    /**
     * 保存文章标签
     *
     * @param article
     * @param articleContentHtml
     * @param userId
     * @return
     * @throws UnsupportedEncodingException
     */
    Integer saveTagArticle(Article article, String articleContentHtml, Long userId) throws UnsupportedEncodingException;


    /**
     * 查找tag
     * @return
     */
    List<LabelModel> findTagLabels();

    /**
     * 清除未使用标签
     *
     * @return
     */
    boolean cleanUnusedTag();

}
