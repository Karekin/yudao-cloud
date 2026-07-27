package cn.iocoder.yudao.module.bpm.api.definition;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmSystemModelRegisterReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.category.BpmCategorySaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmModelConvert;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.service.definition.BpmCategoryService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.flowable.engine.repository.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class BpmSystemModelApiImpl implements BpmSystemModelApi {

    @Resource
    private BpmModelService modelService;
    @Resource
    private BpmCategoryService categoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<String> register(@Valid BpmSystemModelRegisterReqDTO reqDTO) {
        ensureCategory(reqDTO);
        Model model = modelService.getModelList(null).stream()
                .filter(candidate -> reqDTO.getKey().equals(candidate.getKey()))
                .findFirst().orElse(null);
        if (model == null) {
            String modelId = modelService.createModel(toModel(reqDTO, null, List.of(reqDTO.getManagerUserId())));
            modelService.deployModel(reqDTO.getManagerUserId(), modelId);
            return success(modelId);
        }

        BpmModelMetaInfoVO currentMeta = BpmModelConvert.INSTANCE.parseMetaInfo(model);
        List<Long> managers = currentMeta == null || CollUtil.isEmpty(currentMeta.getManagerUserIds())
                ? new ArrayList<>(List.of(reqDTO.getManagerUserId()))
                : new ArrayList<>(currentMeta.getManagerUserIds());
        Long updateUserId = managers.get(0);
        if (!managers.contains(reqDTO.getManagerUserId())) {
            managers.add(reqDTO.getManagerUserId());
        }
        byte[] currentBpmn = modelService.getModelBpmnXML(model.getId());
        boolean changed = !Objects.equals(model.getName(), reqDTO.getName())
                || !Objects.equals(model.getCategory(), reqDTO.getCategoryCode())
                || currentMeta == null
                || !Objects.equals(currentMeta.getDescription(), reqDTO.getDescription())
                || !Objects.equals(currentMeta.getFormCustomCreatePath(), reqDTO.getFormCustomCreatePath())
                || !Objects.equals(currentMeta.getFormCustomViewPath(), reqDTO.getFormCustomViewPath())
                || !Objects.equals(currentMeta.getManagerUserIds(), managers)
                || !Arrays.equals(currentBpmn, reqDTO.getBpmnXml().getBytes(StandardCharsets.UTF_8));
        if (changed) {
            modelService.updateModel(updateUserId, toModel(reqDTO, model.getId(), managers));
        }
        if (changed || model.getDeploymentId() == null) {
            modelService.deployModel(updateUserId, model.getId());
        }
        return success(model.getId());
    }

    private void ensureCategory(BpmSystemModelRegisterReqDTO reqDTO) {
        if (CollUtil.isNotEmpty(categoryService.getCategoryListByCode(List.of(reqDTO.getCategoryCode())))) {
            return;
        }
        categoryService.createCategory(new BpmCategorySaveReqVO()
                .setName(reqDTO.getCategoryName())
                .setCode(reqDTO.getCategoryCode())
                .setDescription("CloudMold Agent 运行中的人工审批流程")
                .setStatus(0)
                .setSort(5));
    }

    private BpmModelSaveReqVO toModel(BpmSystemModelRegisterReqDTO reqDTO, String id, List<Long> managers) {
        BpmModelSaveReqVO model = new BpmModelSaveReqVO();
        model.setId(id);
        model.setKey(reqDTO.getKey());
        model.setName(reqDTO.getName());
        model.setCategory(reqDTO.getCategoryCode());
        model.setBpmnXml(reqDTO.getBpmnXml());
        model.setDescription(reqDTO.getDescription());
        model.setType(BpmModelTypeEnum.BPMN.getType());
        model.setFormType(BpmModelFormTypeEnum.CUSTOM.getType());
        model.setFormCustomCreatePath(reqDTO.getFormCustomCreatePath());
        model.setFormCustomViewPath(reqDTO.getFormCustomViewPath());
        model.setVisible(true);
        model.setStartUserIds(List.of());
        model.setStartDeptIds(List.of());
        model.setManagerUserIds(managers);
        return model;
    }

}
