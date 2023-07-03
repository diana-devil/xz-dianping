package com.xzdp;

import com.xzdp.entity.Shop;
import com.xzdp.entity.ShopType;
import com.xzdp.service.IShopService;
import com.xzdp.service.IShopTypeService;
import com.xzdp.utils.TypeCon;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class testbeanToListBySort {

    @Autowired
    private IShopTypeService shopTypeService;

    @Autowired
    private IShopService shopService;

    @Test
    void beanToListBySorttest() {
        List<Shop> list = shopService.list();
//        System.out.println(TypeCon.beanToListBySort());


        List<ShopType> list1 = shopTypeService.list();
        System.out.println(TypeCon.beanToListBySort(list1));

    }
}
