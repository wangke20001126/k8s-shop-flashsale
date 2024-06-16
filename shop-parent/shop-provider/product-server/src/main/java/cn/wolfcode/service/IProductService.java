package cn.wolfcode.service;

import cn.wolfcode.domain.Product;

import java.util.List;


public interface IProductService {

    Product getById(Long id);

    /**
     * 基于商品 id 列表查询商品列表
     *
     * @param idList id 列表
     * @return 商品列表
     */
    List<Product> queryByIdList(List<Long> idList);
}
