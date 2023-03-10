package com.atguigu.process.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.auth.service.SysUserService;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.vo.process.ProcessQueryVo;
import com.atguigu.vo.process.ProcessVo;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import com.atguigu.model.process.Process;
import org.springframework.util.CollectionUtils;
import springfox.documentation.spring.web.json.Json;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * <p>
 * 审批类型 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-03-08
 */
@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper, Process> implements OaProcessService {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private OaProcessTemplateService processTemplateService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private OaProcessRecordService processRecordService;

    @Autowired
    private HistoryService historyService;

    @Override
    public IPage<ProcessVo> selectPage(Page<ProcessVo> pageParam, ProcessQueryVo processQueryVo) {
        IPage<ProcessVo> pageModel = baseMapper.selectPage(pageParam,processQueryVo);
        return pageModel;
    }

    @Override
    public void deployByZip(String deployPath) {
        InputStream inputStream =
                this.getClass().getClassLoader().getResourceAsStream(deployPath);
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        Deployment deployment = repositoryService.createDeployment().addZipInputStream(zipInputStream).deploy();
        System.out.println(deployment.getName());
        System.out.println(deployment.getName());
    }

    @Override
    public void startUp(ProcessFormVo processFormVo) {
        SysUser sysUser = sysUserService.getById(LoginUserInfoHelper.getUserId());
        ProcessTemplate processTemplate = processTemplateService.getById(processFormVo.getProcessTemplateId());
        Process process = new Process();
        BeanUtils.copyProperties(processFormVo,process);

        process.setStatus(1);
        String workNo = System.currentTimeMillis() + "";
        process.setProcessCode(workNo);
        process.setUserId(LoginUserInfoHelper.getUserId());
        process.setFormValues(processFormVo.getFormValues());
        process.setTitle(sysUser.getName() + "发起" + processTemplate.getName() + "申请");
        baseMapper.insert(process);

        String processDefinitionKey = processTemplate.getProcessDefinitionKey();
        String businessKey = String.valueOf(process.getId());
        String formValues = processFormVo.getFormValues();
        JSONObject jsonObject = JSON.parseObject(formValues);
        JSONObject formData = jsonObject.getJSONObject("formData");
        Map<String,Object> map = new HashMap<>();
        for(Map.Entry<String,Object> entry:formData.entrySet()) {
            map.put(entry.getKey(),entry.getValue());
        }
        Map<String,Object> variables = new HashMap<>();
        variables.put("data",map);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, variables);

        List<Task> taskList = this.getCurrentTaskList(processInstance.getId());
        List<String> nameList = new ArrayList<>();
        for(Task task : taskList) {
            String assigneeName = task.getAssignee();
            SysUser user = sysUserService.getUserByUserName(assigneeName);
            String name = user.getName();
            nameList.add(name);

            //TODO 推送消息
        }
        process.setProcessInstanceId(processInstance.getId());
        process.setDescription("等待" + StringUtils.join(nameList.toArray(), ",") + "审批");

        baseMapper.updateById(process);

        processRecordService.record(process.getId(),1,"发起申请");
    }

    @Override
    public IPage<ProcessVo> findfindPending(Page<Process> pageParam) {
        TaskQuery query = taskService.createTaskQuery()
                .taskAssignee(LoginUserInfoHelper.getUsername())
                .orderByTaskCreateTime()
                .desc();
        int begin = (int)((pageParam.getCurrent()-1) * pageParam.getSize());
        int size =  (int)pageParam.getSize();
        List<Task> taskList = query.listPage(begin, size);
        long totalCount = query.count();
        List<ProcessVo> processVoList = new ArrayList<>();
        for(Task task : taskList) {
            String processInstanceId = task.getProcessInstanceId();
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            String businessKey = processInstance.getBusinessKey();
            if(businessKey == null) {
                continue;
            }
            long processId = Long.parseLong(businessKey);
            Process process = baseMapper.selectById(processId);
            ProcessVo processVo = new ProcessVo();
            BeanUtils.copyProperties(process,processVo);
            processVo.setTaskId(task.getId());
            processVoList.add(processVo);
        }
        IPage<ProcessVo> page = new Page<ProcessVo>(pageParam.getCurrent(),
                                                    pageParam.getSize(),totalCount);
        page.setRecords(processVoList);
        return page;
    }

    @Override
    public Map<String, Object> show(Long id) {
        Process process = baseMapper.selectById(id);

        LambdaQueryWrapper<ProcessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessRecord::getProcessId,id);
        List<ProcessRecord> processRecordlist = processRecordService.list(wrapper);
        ProcessTemplate processTemplate = processTemplateService.getById(process.getProcessTemplateId());
        boolean isApprove = false;
        List<Task> taskList = this.getCurrentTaskList(process.getProcessInstanceId());
        for(Task task : taskList) {
            String username = LoginUserInfoHelper.getUsername();
            if(task.getAssignee().equals(username)) {
                isApprove = true;
            }
        }
        Map<String,Object> map = new HashMap<>();
        map.put("process",process);
        map.put("processRecordList",processRecordlist);
        map.put("processTemplate",processTemplate);
        map.put("isApprove",isApprove);
        return map;
    }

    @Override
    public void approve(ApprovalVo approvalVo) {
        String taskId = approvalVo.getTaskId();
        Map<String, Object> variables1 = taskService.getVariables(taskId);
        for (Map.Entry<String, Object> entry : variables1.entrySet()) {
            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
        }
        if (approvalVo.getStatus() == 1) {
            //已通过
            Map<String, Object> variables = new HashMap<>();
            taskService.complete(taskId, variables);
        } else {
            //驳回
            this.endTask(taskId);
        }
        String description = approvalVo.getStatus().intValue() == 1 ? "已通过" : "驳回";
        processRecordService.record(approvalVo.getProcessId(), approvalVo.getStatus(), description);

        //计算下一个审批人
        Process process = baseMapper.selectById(approvalVo.getProcessId());
        List<Task> taskList = this.getCurrentTaskList(process.getProcessInstanceId());
        if (!CollectionUtils.isEmpty(taskList)) {
            List<String> assigneeList = new ArrayList<>();
            for(Task task : taskList) {
                String assignee = task.getAssignee();
                SysUser sysUser = sysUserService.getUserByUserName(assignee);
                assigneeList.add(sysUser.getName());

                //推送消息给下一个审批人
            }
            process.setDescription("等待" + StringUtils.join(assigneeList.toArray(), ",") + "审批");
            process.setStatus(1);
        } else {
            if(approvalVo.getStatus().intValue() == 1) {
                process.setDescription("审批完成（同意）");
                process.setStatus(2);
            } else {
                process.setDescription("审批完成（拒绝）");
                process.setStatus(-1);
            }
        }
        //推送消息给申请人
        baseMapper.updateById(process);
    }


    private void endTask(String taskId) {
        //  当前任务
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();

        BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        List endEventList = bpmnModel.getMainProcess().findFlowElementsOfType(EndEvent.class);
        // 并行任务可能为null
        if(CollectionUtils.isEmpty(endEventList)) {
            return;
        }
        FlowNode endFlowNode = (FlowNode) endEventList.get(0);
        FlowNode currentFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(task.getTaskDefinitionKey());

        //  临时保存当前活动的原始方向
        List originalSequenceFlowList = new ArrayList<>();
        originalSequenceFlowList.addAll(currentFlowNode.getOutgoingFlows());
        //  清理活动方向
        currentFlowNode.getOutgoingFlows().clear();

        //  建立新方向
        SequenceFlow newSequenceFlow = new SequenceFlow();
        newSequenceFlow.setId("newSequenceFlow");
        newSequenceFlow.setSourceFlowElement(currentFlowNode);
        newSequenceFlow.setTargetFlowElement(endFlowNode);
        List newSequenceFlowList = new ArrayList<>();
        newSequenceFlowList.add(newSequenceFlow);
        //  当前节点指向新的方向
        currentFlowNode.setOutgoingFlows(newSequenceFlowList);

        //  完成当前任务
        taskService.complete(taskId);
    }

    private List<Task> getCurrentTaskList(String id) {
        List<org.activiti.engine.task.Task> taskList = taskService.createTaskQuery().processInstanceId(id).list();
        return taskList;
    }

    @Override
    public IPage<ProcessVo> findProcessed(Page<Process> pageParam) {
        // 根据当前人的ID查询
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery().taskAssignee(LoginUserInfoHelper.getUsername()).finished().orderByTaskCreateTime().desc();
        List<HistoricTaskInstance> list = query.listPage((int) ((pageParam.getCurrent() - 1) * pageParam.getSize()), (int) pageParam.getSize());
        long totalCount = query.count();

        List<ProcessVo> processVoList = new ArrayList<>();
        for (HistoricTaskInstance item : list) {
            String processInstanceId = item.getProcessInstanceId();
            LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Process::getProcessInstanceId, processInstanceId);
            Process process = baseMapper.selectOne(wrapper);
            ProcessVo processVo = new ProcessVo();
            BeanUtils.copyProperties(process, processVo);
            processVoList.add(processVo);
        }
        IPage<ProcessVo> page = new Page<ProcessVo>(pageParam.getCurrent(), pageParam.getSize(), totalCount);
        page.setRecords(processVoList);
        return page;
    }

    @Override
    public IPage<ProcessVo> findStarted(Page<ProcessVo> pageParam) {
        ProcessQueryVo processQueryVo = new ProcessQueryVo();
        processQueryVo.setUserId(LoginUserInfoHelper.getUserId());
        IPage<ProcessVo> pageModel = baseMapper.selectPage(pageParam,processQueryVo);
        return pageModel;
    }

}
