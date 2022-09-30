/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.api

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.pojo.BuildFormValue
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.model.process.tables.records.TPipelineInfoRecord
import com.tencent.devops.process.api.user.UserBuildParametersResource
import com.tencent.devops.process.engine.pojo.PipelineFilterByLabelInfo
import com.tencent.devops.process.engine.pojo.PipelineFilterParam
import com.tencent.devops.process.engine.service.PipelineRuntimeService
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.BuildFormRepositoryValue
import com.tencent.devops.process.pojo.Pipeline
import com.tencent.devops.process.pojo.PipelineIdAndName
import com.tencent.devops.process.pojo.classify.PipelineViewFilterByName
import com.tencent.devops.process.pojo.classify.enums.Condition
import com.tencent.devops.process.pojo.classify.enums.Logic
import com.tencent.devops.process.service.PipelineListFacadeService
import com.tencent.devops.process.utils.PIPELINE_BUILD_ID
import com.tencent.devops.process.utils.PIPELINE_BUILD_NUM
import com.tencent.devops.process.utils.PIPELINE_ELEMENT_ID
import com.tencent.devops.process.utils.PIPELINE_ID
import com.tencent.devops.process.utils.PIPELINE_NAME
import com.tencent.devops.process.utils.PIPELINE_START_TYPE
import com.tencent.devops.process.utils.PIPELINE_START_USER_NAME
import com.tencent.devops.process.utils.PIPELINE_VMSEQ_ID
import com.tencent.devops.process.utils.PROJECT_NAME
import com.tencent.devops.repository.api.ServiceRepositoryResource
import com.tencent.devops.repository.pojo.enums.Permission
import com.tencent.devops.store.pojo.app.BuildEnvParameters
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Suppress("UNUSED")
@RestResource
class UserBuildParametersResourceImpl @Autowired constructor(
    private val client: Client,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineListFacadeService: PipelineListFacadeService
) : UserBuildParametersResource {

    companion object {
        private val logger = LoggerFactory.getLogger(UserBuildParametersResourceImpl::class.java)
    }

    private val result = Result(
        data = listOf(
            BuildEnvParameters(name = PIPELINE_START_USER_NAME, desc = "当前构建的启动人"),
            BuildEnvParameters(
                name = PIPELINE_START_TYPE,
                desc = "当前构建的启动方式，从${StartType.values().joinToString("/") { it.name }}中取值"
            ),
            BuildEnvParameters(name = PIPELINE_BUILD_NUM, desc = "当前构建的唯一标示ID，从1开始自增"),
            BuildEnvParameters(name = PROJECT_NAME, desc = "项目英文名"),
            BuildEnvParameters(name = PIPELINE_ID, desc = "流水线ID"),
            BuildEnvParameters(name = PIPELINE_NAME, desc = "流水线名称"),
            BuildEnvParameters(name = PIPELINE_BUILD_ID, desc = "当前构建ID"),
            BuildEnvParameters(name = PIPELINE_VMSEQ_ID, desc = "流水线JOB ID"),
            BuildEnvParameters(name = PIPELINE_ELEMENT_ID, desc = "流水线Task ID")
        )
    )

    override fun getCommonBuildParams(userId: String): Result<List<BuildEnvParameters>> {
        return result
    }

    override fun listRepositoryAliasName(
        userId: String,
        projectId: String,
        repositoryType: String?,
        permission: Permission,
        aliasName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<List<BuildFormValue>> {
        return Result(
            listRepositoryInfo(
                userId = userId,
                projectId = projectId,
                repositoryType = repositoryType,
                page = page,
                pageSize = pageSize,
                aliasName = aliasName
            ).map { BuildFormValue(it.aliasName, it.aliasName) }
        )
    }

    @SuppressWarnings("LongParameterList")
    private fun listRepositoryInfo(
        userId: String,
        projectId: String,
        repositoryType: String?,
        page: Int?,
        pageSize: Int?,
        aliasName: String?
    ) = try {
        client.get(ServiceRepositoryResource::class).hasPermissionList(
            userId = userId,
            projectId = projectId,
            permission = Permission.LIST,
            repositoryType = repositoryType,
            page = page,
            pageSize = pageSize,
            aliasName = aliasName
        ).data?.records ?: emptyList()
    } catch (ignore: Exception) {
        logger.warn("[$userId|$projectId] Fail to get the repository list", ignore)
        emptyList()
    }

    override fun listRepositoryHashId(
        userId: String,
        projectId: String,
        repositoryType: String?,
        permission: Permission,
        aliasName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<List<BuildFormRepositoryValue>> {
        return Result(
            listRepositoryInfo(
                userId = userId,
                projectId = projectId,
                repositoryType = repositoryType,
                page = page,
                pageSize = pageSize,
                aliasName = aliasName
            ).map { BuildFormRepositoryValue(id = it.repositoryHashId!!, name = it.aliasName) }
        )
    }

    override fun listPipelineName(
        userId: String,
        projectId: String,
        pipelineId: String,
        aliasName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<List<BuildFormValue>> {
        try {
            val result = listSubPipelineInfo(
                userId = userId,
                projectId = projectId,
                aliasName = aliasName,
                pageSize = pageSize,
                page = page
            )
            return Result(
                result.filter { !it.pipelineId.contains(pipelineId) }
                    .map { BuildFormValue(it.pipelineName, it.pipelineName) }
            )
        } catch (ignore: Exception) {
            logger.warn("[$userId|$projectId] Fail to get the repository list", ignore)
            return Result(emptyList())
        }
    }

    override fun listPipelineIdAndName(
        userId: String,
        projectId: String,
        pipelineId: String?,
        aliasName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<Page<Pipeline>> {
        try {
            val result = listSubPipelineInfo(
                userId = userId,
                projectId = projectId,
                aliasName = aliasName,
                pageSize = pageSize,
                page = page
            ).filter { pipelineId == null || !it.pipelineId.contains(pipelineId) }
                .map {
                    Pipeline(
                        projectId = it.projectId,
                        pipelineId = it.pipelineId,
                        pipelineName = it.pipelineName,
                        taskCount = it.taskCount,
                        canManualStartup = it.manualStartup == 1,
                        latestBuildEstimatedExecutionSeconds = 1L,
                        deploymentTime = (it.updateTime)?.timestampmilli() ?: 0,
                        createTime = (it.createTime)?.timestampmilli() ?: 0,
                        updateTime = (it.updateTime)?.timestampmilli() ?: 0,
                        pipelineVersion = it.version,
                        currentTimestamp = System.currentTimeMillis(),
                        hasPermission = true,
                        hasCollect = false,
                        updater = it.lastModifyUser,
                        creator = it.creator
                    )
                }

            return Result(
                data = Page(
                    page = page ?: 0,
                    pageSize = pageSize ?: -1,
                    count = result.size + 0L,
                    records = result
                )
            )
        } catch (ignore: Exception) {
            logger.warn("[$userId|$projectId] Fail to get the repository list", ignore)
            return Result(
                data = Page(
                    page = page ?: 0,
                    pageSize = pageSize ?: -1,
                    count = 0L,
                    records = emptyList()
                )
            )
        }
    }

    private fun listSubPipelineInfo(
        userId: String,
        projectId: String,
        aliasName: String?,
        page: Int?,
        pageSize: Int?
    ): List<TPipelineInfoRecord> {
        val hasPermissionList =
            pipelinePermissionService.getResourceByPermission(
                userId = userId,
                projectId = projectId,
                permission = AuthPermission.EXECUTE
            )

        val pipelineFilterParamList = if (!aliasName.isNullOrBlank()) {
            listOf(
                PipelineFilterParam(
                    logic = Logic.AND,
                    filterByPipelineNames = listOf(
                        PipelineViewFilterByName(
                            condition = Condition.LIKE,
                            pipelineName = aliasName
                        )
                    ),
                    filterByPipelineCreators = emptyList(),
                    filterByLabelInfo = PipelineFilterByLabelInfo(
                        filterByLabels = emptyList(),
                        labelToPipelineMap = null
                    )
                )
            )
        } else {
            null
        }
        return client.get(PipelineRuntimeService::class).getBuildPipelineRecords(
            projectId = projectId,
            channelCode = ChannelCode.BS,
            pipelineIds = hasPermissionList,
            pipelineFilterParamList = pipelineFilterParamList,
            pageSize = pageSize,
            page = page
        )
    }
}
