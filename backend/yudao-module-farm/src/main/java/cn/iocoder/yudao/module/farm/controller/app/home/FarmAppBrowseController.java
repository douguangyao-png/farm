package cn.iocoder.yudao.module.farm.controller.app.home;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.farm.controller.admin.supply.vo.FarmSupplyRespVO;
import cn.iocoder.yudao.module.farm.controller.app.article.vo.FarmAppArticleDetailRespVO;
import cn.iocoder.yudao.module.farm.controller.app.article.vo.FarmAppArticleRespVO;
import cn.iocoder.yudao.module.farm.controller.app.home.vo.FarmAppHomeRespVO;
import cn.iocoder.yudao.module.farm.controller.app.home.vo.FarmAppProductDetailRespVO;
import cn.iocoder.yudao.module.farm.service.FarmAppBrowseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** C 端浏览类接口（不需要登录，仍要带 tenant-id）：/app-api/farm/**（mock：farm-app.ts 17–58、280–292 行） */
@Tag(name = "用户 App - 认养农场 - 浏览")
@RestController
@RequestMapping("/farm")
@PermitAll
public class FarmAppBrowseController {

    @Autowired
    private FarmAppBrowseService service;

    /** mock 18 行 */
    @GetMapping("/home")
    public CommonResult<FarmAppHomeRespVO> home() {
        return success(service.home());
    }

    /** mock 37 行 */
    @GetMapping("/product/get")
    public CommonResult<FarmAppProductDetailRespVO> getProduct(@RequestParam("id") Long id) {
        return success(service.getProduct(id));
    }

    /** mock 49 行 */
    @GetMapping("/supply/list")
    public CommonResult<List<FarmSupplyRespVO>> supplyList(@RequestParam(value = "cat", required = false) String cat) {
        return success(service.supplyList(cat));
    }

    /** mock 54 行 */
    @GetMapping("/supply/get")
    public CommonResult<FarmSupplyRespVO> getSupply(@RequestParam("id") Long id) {
        return success(service.getSupply(id));
    }

    /** mock 281 行 */
    @GetMapping("/article/list")
    public CommonResult<List<FarmAppArticleRespVO>> articleList() {
        return success(service.articleList());
    }

    /** mock 286 行 */
    @GetMapping("/article/get")
    public CommonResult<FarmAppArticleDetailRespVO> getArticle(@RequestParam("id") Long id) {
        return success(service.getArticle(id));
    }
}
