package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRegistryVersionMapper extends BaseMapperX<WorkflowRegistryVersionDO> {

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_registry_version WHERE tenant_id=#{tenantId} AND registry_version_id=#{versionId}")
    WorkflowRegistryVersionDO selectByRegistryVersionId(@Param("tenantId") Long tenantId,
                                                        @Param("versionId") String versionId);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_registry_version WHERE tenant_id=#{tenantId} AND proposal_id=#{proposalId}")
    WorkflowRegistryVersionDO selectByProposalId(@Param("tenantId") Long tenantId,
                                                 @Param("proposalId") String proposalId);
}
