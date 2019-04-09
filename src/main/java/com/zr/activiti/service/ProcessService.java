package com.zr.activiti.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.identity.Authentication;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.stereotype.Service;

import com.zr.activiti.entity.BaseVO;
import com.zr.activiti.entity.Page;
import com.zr.activiti.utils.ProcessDefinitionCache;
import com.zr.activiti.utils.StringUtil;


/**
 * 流程处理service
 * 
 * @author Administrator
 *
 */
@Service
public class ProcessService{
	@Resource
	RepositoryService repositoryService;
	@Resource
	RuntimeService runtimeService;
	@Resource
	HistoryService historyService;
	@Resource
	CusTaskService cusTaskService;


	/**
	 * 查询已部署的流程定义列表<br/>
	 * select distinct RES.* from ACT_RE_PROCDEF RES order by RES.VERSION_ asc LIMIT
	 * ? OFFSET ?
	 */
	
	public List<ProcessDefinition> findDeployedProcessList() {
		List<ProcessDefinition> processList = repositoryService.createProcessDefinitionQuery()
				.orderByProcessDefinitionVersion().asc()// 升序
				.list();
		return processList;
	}

	/**
	 * 根据流程定义id查询已部署的流程定义对象
	 *
	 * @param processDefinitionId
	 *            流程定义ID
	 * @return
	 */
	
	public ProcessDefinition findProcessDefinitionById(String processDefinitionId) {
		ProcessDefinition processDefinition = this.repositoryService.createProcessDefinitionQuery()
				.processDefinitionId(processDefinitionId).singleResult();
		return processDefinition;
	}

	/**
	 * 根据流程定义id查询已部署的流程定义对象
	 *
	 * @param processDefinitionId
	 *            流程定义ID
	 * @return
	 */
	
	public ProcessDefinition findProcessDefinitionByKey(String processDefKey) {
		List<ProcessDefinition> proceDefList = this.repositoryService.createProcessDefinitionQuery()
				.processDefinitionKey(processDefKey).orderByProcessDefinitionVersion().desc().list();
		ProcessDefinition processDefinition = proceDefList.get(0);
		return processDefinition;
	}

	/**
	 * 根据流程部署id查询已部署的流程定义对象
	 *
	 * @param deploymentId
	 *            部署id
	 * @return
	 */
	public ProcessDefinition findProcessDefinitionByDeploymentId(String deploymentId) {
		ProcessDefinition processDefinition = this.repositoryService.createProcessDefinitionQuery()
				.deploymentId(deploymentId).singleResult();
		return processDefinition;
	}
	
	
	public void deleteDeployedProcess(String deploymentId) throws Exception {
		// 普通删除，如果当前规则下有正在执行的流程，则抛出异常
		// repositoryService.deleteDeployment(deploymentId);
		// 级联删除，会删除和当前规则相关的所有信息，正在执行的信息，也包括历史信息
		repositoryService.deleteDeployment(deploymentId, true);
	}

	/**
	 * 启动流程
	 */
	
	public ProcessInstance startWorkFlow(BaseVO baseVO, Map<String, Object> variables) throws Exception {

		final String businesskey = baseVO.getBusinessKey();// 设置业务key
		final String procDefKey = businesskey.split("\\:")[0];
		// 根据流程定义KEY查询最新版本的流程定义
		ProcessDefinitionEntity procDef = (ProcessDefinitionEntity) findProcessDefinitionByKey(procDefKey);
		if (procDef == null) {
			throw new RuntimeException("流程定义KEY为" + procDefKey + "流程定义未找到，请重新发布");
		}
		Authentication.setAuthenticatedUserId(baseVO.getCreateId());// 设置流程的发起人start_userId

		ProcessInstance instance = runtimeService.startProcessInstanceByKey(procDefKey, businesskey, variables);

		return instance;
	}

	/**
	 * 根据流程实例id查询该流程实例对象
	 * 
	 * @param processInstanceId
	 * @return
	 */
	
	public ProcessInstance getProcessInstanceById(String processInstanceId) {
		ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
				.processInstanceId(processInstanceId).singleResult();
		return processInstance;

	}

	/**
	 * 根据部署id查询流程实例(可能启动了多个实例，所以返回列表)
	 * @param deploymentId 部署id
	 * @return
	 */
	public List<HistoricProcessInstance> getPIsByDeploymentId(String deploymentId){
		HistoricProcessInstanceQuery historQuery = historyService.createHistoricProcessInstanceQuery().deploymentId(deploymentId).orderByProcessInstanceStartTime().desc();
		if (historQuery == null)
			return new ArrayList<>();
		List<HistoricProcessInstance> list = historQuery.list();
		return list;
	}

	/**
	 * 查询我的流程实例（包括结束的和正在运行的）<br/>
	 * 
	 * @return
	 * @throws Exception
	 */
	
	public List<BaseVO> findMyProcessInstances(Page<BaseVO> page, String userCode) throws Exception {

		HistoricProcessInstanceQuery historQuery = historyService.createHistoricProcessInstanceQuery()
				.startedBy(userCode).orderByProcessInstanceStartTime().desc();

		List<BaseVO> processList = queryHistoryProcessInstanceList(page, historQuery);
		System.out.println("processList:" + processList);
		return processList;
	}

	/**
	 * 查询已结束的流程实例<br/>
	 * select distinct RES.* , DEF.KEY_ as PROC_DEF_KEY_, DEF.NAME_ as
	 * PROC_DEF_NAME_, DEF.VERSION_ as PROC_DEF_VERSION_, DEF.DEPLOYMENT_ID_ as
	 * DEPLOYMENT_ID_ from ACT_HI_PROCINST RES left outer join ACT_RE_PROCDEF DEF on
	 * RES.PROC_DEF_ID_ = DEF.ID_ WHERE RES.END_TIME_ is not NULL and (
	 * exists(select LINK.USER_ID_ from ACT_HI_IDENTITYLINK LINK where USER_ID_ = ?
	 * and LINK.PROC_INST_ID_ = RES.ID_) ) order by RES.ID_ asc LIMIT ? OFFSET ?
	 * 
	 * @return
	 */
	
	public List<BaseVO> findFinishedProcessInstances(Page<BaseVO> page, String userCode, boolean isInvolved) {
		HistoricProcessInstanceQuery historQuery = null;
		if (isInvolved) {
			historQuery = getHisProInsQueryInvolvedUser(userCode);
		} else {
			historQuery = getHisProInsQueryStartedBy(userCode);
		}

		List<BaseVO> processList = queryHistoryProcessInstanceList(page, historQuery);
		return processList;
	}


	/**
	 * 查询指定用户发起的流程 （流程历史 用户发起 ）
	 * 
	 * @param userCode
	 * @return
	 */
	private HistoricProcessInstanceQuery getHisProInsQueryStartedBy(String userCode) {
		HistoricProcessInstanceQuery historQuery = historyService.createHistoricProcessInstanceQuery()
				.startedBy(userCode).finished().orderByProcessInstanceEndTime().desc();
		return historQuery;
	}

	/**
	 * 查询指定用户参与的流程信息 （流程历史 用户参与 ）
	 * 
	 * @param userCode
	 * @return
	 */
	private HistoricProcessInstanceQuery getHisProInsQueryInvolvedUser(String userCode) {
		HistoricProcessInstanceQuery historQuery = historyService.createHistoricProcessInstanceQuery()
				.involvedUser(userCode).finished().orderByProcessInstanceEndTime().desc();
		return historQuery;
	}

	
	private List<BaseVO> queryHistoryProcessInstanceList(Page<BaseVO> page, HistoricProcessInstanceQuery historQuery) {
		if (historQuery == null)
			return new ArrayList<>();

		List<HistoricProcessInstance> list = new ArrayList<>();
		list = historQuery.list();
		if(null != page) {//分页
//			Integer totalSum = historQuery.list().size();
//			int[] pageParams = page.getPageParams(totalSum);
//			list = historQuery.listPage(pageParams[0], pageParams[1]);
			int[] pageParams = page.getPageParams(list.size());
			list = list.subList(pageParams[0], pageParams[0]+pageParams[1]);
		}
		
		List<BaseVO> processList = castProcessToBaseVo(list);
		return processList;
	}

	private List<BaseVO> castProcessToBaseVo(List<HistoricProcessInstance> list) {
		List<BaseVO> processList = new ArrayList<>();
		for (HistoricProcessInstance historicProcessInstance : list) {
			String processInstanceId = historicProcessInstance.getId();
			
			BaseVO base = null;
			if (null != historicProcessInstance.getEndTime()) {
				base = getBaseVOFromHistoryVariable(null, historicProcessInstance, processInstanceId);
				base.setDeleteReason(historicProcessInstance.getDeleteReason());
				base.setEnd(true);
			}else {
				base = (BaseVO) getBaseVOFromRu_Variable(processInstanceId);
			}

			Task task = null;
			String candidateUserIds = "";
			
			List<Task> taskList = cusTaskService.findRunTaskByProcInstanceId(processInstanceId);
			if (null != taskList && taskList.size() > 0) {
				task = taskList.get(0);
				candidateUserIds = cusTaskService.getCandidateIdsOfTask(taskList);
			}
			if(null != base) {
				if(null != task) {
					base.setTask(task);
					base.setSuspended(task.isSuspended());
					base.setTaskDefinitionKey(task.getTaskDefinitionKey());
					base.setToHandleTaskName(task.getName());
					base.setToHandleTaskId(task.getId());
				}

				ProcessDefinition process = findProcessDefinitionById(historicProcessInstance.getProcessDefinitionId());
				if(null != process) {
					base.setDescription(process.getDescription());
					base.setProcessDefinition(process);
					base.setProcessDefinitionKey(process.getKey());
					base.setProcessDefinitionId(process.getId());
					base.setProcessDefinitionName(process.getName());
				}
				base.setAssign(candidateUserIds);
				base.setProcessInstanceId(processInstanceId);

				base.setHistoricProcessInstance(historicProcessInstance);
				base.setDeploymentId(historicProcessInstance.getDeploymentId());
				processList.add(base);
			}
		}
		return processList;
	}

	/**
	 * 根据流程实例id获取历史流程实例
	 * @param processInstanceId
	 * @return
	 */
	
	public HistoricProcessInstance getHisProcessInstanceByInstanceId(String processInstanceId) {
		return historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).orderByProcessInstanceEndTime().desc().singleResult();
	}
	
	
	public void deleteProcessInstance(String processInstanceId, String deleteReason) throws Exception {
		deleteProcessInstance(processInstanceId, deleteReason, false);
	}

	/**
	 * 删除流程实例，可级联删除
	 */
	
	public void deleteProcessInstance(String processInstanceId, String deleteReason, boolean cascade) throws Exception {
		boolean isEnd = isProcessEnd(processInstanceId);
		System.out.println("ProcessService deleteProcessInstance processInstanceId:" + processInstanceId
				+ ";isProcessEnd:" + isEnd);
		if (!isEnd) {
			runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
		}
		if (cascade) {
			historyService.deleteHistoricProcessInstance(processInstanceId);
		}
	}

	/**
	 * 判断流程是否已经结束
	 * 
	 * @param processInstanceId
	 * @return
	 */
	
	public boolean isProcessEnd(String processInstanceId) {
		return historyService.createHistoricProcessInstanceQuery().finished()
				.processInstanceId(processInstanceId).count() > 0;
//
//		ProcessInstance processInstance = getProcessInstanceById(processInstanceId);
//		if (processInstance == null) {
//			return true;
//		} else {
//			return false;
//		}
	}

	/**
	 * 获取当前流程下的key为entity的variable<br/>
	 * select * from ACT_RU_EXECUTION where ID_ = ?<br/>
	 * select * from ACT_RU_VARIABLE where EXECUTION_ID_ = ? and NAME_= ? and
	 * TASK_ID_ is null<br/>
	 * select * from ACT_GE_BYTEARRAY where ID_ = ?<br/>
	 */
	public BaseVO getBaseVOFromRu_Variable(String processInstanceId) {
		BaseVO base = (BaseVO) getRunVariable("entity", processInstanceId);
		return base;
	}

	public Object getRunVariable(String variableKey, String processInstanceId) {
		boolean hasKey = this.runtimeService.hasVariable(processInstanceId, variableKey);
		if (!hasKey)
			return null;
		Object obj = this.runtimeService.getVariable(processInstanceId, variableKey);
		return obj;
	}
	
	/**
	 * 根据流程实例id获取所有的流程变量
	 * @param processInstanceId
	 * @return
	 */
	public Map<String, Object> getRunVariables(String processInstanceId) {
		Map<String, Object> obj = this.runtimeService.getVariables(processInstanceId);
		return obj;
	}

	/**
	 * select RES.* from ACT_HI_VARINST RES WHERE RES.PROC_INST_ID_ = ? order by
	 * RES.ID_ asc LIMIT ? OFFSET ?<br/>
	 * select * from ACT_GE_BYTEARRAY where ID_ = ? <br/>
	 * 
	 * @param historicTaskInstance
	 * @param historicProcessInstance
	 * @param processInstanceId
	 * @return
	 */
	
	public BaseVO getBaseVOFromHistoryVariable(HistoricTaskInstance historicTaskInstance,
			HistoricProcessInstance historicProcessInstance, String processInstanceId) {

		BaseVO base = (BaseVO) getHistoryVariable("entity", true, processInstanceId, historicTaskInstance,
				historicProcessInstance);
		return base;
	}

	private Object getHistoryVariable(String variableKey, boolean isSerializable, String processInstanceId,
			HistoricTaskInstance historicTaskInstance, HistoricProcessInstance historicProcessInstance) {

		List<HistoricVariableInstance> listVar = this.historyService.createHistoricVariableInstanceQuery()
				.processInstanceId(processInstanceId).list();
		listSort(listVar);
		Object obj = null;
		for (HistoricVariableInstance var : listVar) {
			if (isSerializable) {
				if ("serializable".equals(var.getVariableTypeName()) && variableKey.equals(var.getVariableName())) {
					if (historicTaskInstance != null) {
						if (historicTaskInstance.getId().equals(var.getTaskId())) {
							obj = var.getValue();
							break;
						}
					} else if (historicProcessInstance != null) {
						obj = var.getValue();
						break;
					}
				}
			} else {
				if (variableKey.equals(var.getVariableName())) {
					if (historicTaskInstance != null) {
						if (historicTaskInstance.getId().equals(var.getTaskId())) {
							obj = var.getValue();
							break;
						}
					} else if (historicProcessInstance != null) {
						obj = var.getValue();
						break;
					}
				}
			}
		}
		return obj;
	}

	/**
	 * 对历史变量列表按照创建时间排序
	 * 
	 * @param list
	 */
	private static void listSort(List<HistoricVariableInstance> list) {
		Collections.sort(list, new Comparator<HistoricVariableInstance>() {
			
			public int compare(HistoricVariableInstance o1, HistoricVariableInstance o2) {
				try {
					Date dt1 = o1.getCreateTime();
					Date dt2 = o2.getCreateTime();
					if (dt1 == null || dt2 == null)
						return -1;
					if (dt1.getTime() >= dt2.getTime()) {
						return -1;
					} else {
						return 1;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return 0;
			}
		});
	}

	/**
	 * 显示图片-通过部署ID，不带流程跟踪
	 * 
	 * @param resourceType
	 * @param processDefinitionId
	 * @return
	 * @throws Exception
	 */
	
	public InputStream getDiagramByProDefinitionId_noTrace(String resourceType, String deploymentId) throws Exception {
		ProcessDefinition processDefinition = findProcessDefinitionByDeploymentId(deploymentId);
		String resourceName = "";
		if (resourceType.equals("png") || resourceType.equals("image")) {
			resourceName = processDefinition.getDiagramResourceName();
		} else if (resourceType.equals("xml")) {
			resourceName = processDefinition.getResourceName();
		}
		InputStream resourceAsStream = null;
		if (StringUtil.isNotEmpty(resourceName)) {
			resourceAsStream = repositoryService.getResourceAsStream(deploymentId, resourceName);
		}
		return resourceAsStream;
	}

	/**
	 * 获取流程图像，已执行节点和流程线高亮显示<br/>
	 * select distinct RES.* , DEF.KEY_ as PROC_DEF_KEY_, DEF.NAME_ as
	 * PROC_DEF_NAME_, DEF.VERSION_ as PROC_DEF_VERSION_, DEF.DEPLOYMENT_ID_ as
	 * DEPLOYMENT_ID_ from ACT_HI_PROCINST RES left outer join ACT_RE_PROCDEF DEF on
	 * RES.PROC_DEF_ID_ = DEF.ID_ WHERE RES.PROC_INST_ID_ = ? order by RES.ID_ asc
	 * LIMIT ? OFFSET ?
	 */
	public InputStream getActivitiProccessImage(String processInstanceId) throws Exception {
		System.out.println("[开始]-获取流程图图像 processInstanceId：" + processInstanceId);
		try {
			// 获取历史流程实例
			HistoricProcessInstance historicProcessInstance = getHisProcessInstanceByInstanceId(processInstanceId);
			if (historicProcessInstance == null) {
				throw new Exception();
			} else {
				List<HistoricActivityInstance> historicActivityInstanceList = getHistoricActivityInstanceList(
						processInstanceId);
				// 已执行的节点ID集合
				List<String> executedActivityIdList = new ArrayList<String>();
				int index = 1;
				for (HistoricActivityInstance activityInstance : historicActivityInstanceList) {
					executedActivityIdList.add(activityInstance.getActivityId());
					System.out.println("第[" + index + "]个已执行节点=" + activityInstance.getActivityId() + " : "
							+ activityInstance.getActivityName());
					index++;
				}

				// 获取流程定义
				ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) ProcessDefinitionCache.get().getProcessDefination(historicProcessInstance.getProcessDefinitionId());
				// 获取流程图图像字符流
				InputStream imageStream = com.zr.activiti.utils.ProcessDiagramGenerator
						.generateDiagram(processDefinition, "png", executedActivityIdList);
				System.out.println("[完成]-获取流程图图像");
				return imageStream;
			}
		} catch (Exception e) {
			System.out.println("【异常】-获取流程图失败！" + e.getMessage());
			throw new Exception(e);
		}
	}

	/**
	 * 获取流程历史中已执行节点，并按照节点在流程中执行先后顺序排序
	 * @param processInstanceId
	 * @return
	 */
	private List<HistoricActivityInstance> getHistoricActivityInstanceList(String processInstanceId) {
		List<HistoricActivityInstance> historicActivityInstanceList = historyService
				.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
				.orderByHistoricActivityInstanceId().asc().list();
		historicActivityInstanceList.sort(new Comparator<HistoricActivityInstance>() {
			
			public int compare(HistoricActivityInstance o1, HistoricActivityInstance o2) {
				try {
					Date dt1 = o1.getEndTime();
					Date dt2 = o2.getEndTime();
					if (dt1 == null || dt2 == null)
						return 1;
					if (dt1.getTime() >= dt2.getTime()) {
						return 1;
					} else {
						return -1;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return 0;
			}
		});
		return historicActivityInstanceList;
	}
	

	/**
	 * 运行中的流程 <br/>
	 * 查询ACT_RU_EXECUTION res、ACT_RE_PROCDEF p表on res.PROC_DEF_ID_ = p.ID_
	 */
	
	public List<BaseVO> listRuningProcess(String userCode, Page<BaseVO> page) throws Exception {

//		ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery();
//		int[] pageParams = page.getPageParams(processInstanceQuery.list().size());
//		List<ProcessInstance> list = processInstanceQuery.orderByProcessInstanceId().desc().listPage(pageParams[0],
//				pageParams[1]);
		List<BaseVO> baseVoList = null;
//		List<BaseVO> baseVoList = castProcessToBaseVo(userCode, list);
		return baseVoList;
	}

	/**
	 * 激活流程实例
	 */
	public void activateProcessInstance(String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
	}

	/**
	 * 根据流程key激活流程定义
	 * @param processDefinitionKey
	 */
	public void activateProcessDefination(String processDefinitionKey) {
		repositoryService.activateProcessDefinitionByKey(processDefinitionKey, true, null);
	}
	
	/**
	 * 根据一个流程实例的id挂起该流程实例
	 */
	public void suspendProcessInstance(String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
	}

	/**
	 * 根据流程key暂停流程定义
	 */
	public void suspendProcessDefinition(String processDefinitionKey) {
//        repositoryService.suspendProcessDefinitionByKey(processDefinitionKey);//这时可以继续运行，报错：流程已被挂起
        //根据流程定义的key暂停一个流程定义,并且级联挂起该流程定义下的流程实例,这样流程无法运行
        repositoryService.suspendProcessDefinitionByKey(processDefinitionKey, true, null);
	}

//	private List<BaseVO> castProcessToBaseVo(String userCode, List<ProcessInstance> list) {
//		List<BaseVO> processList = new ArrayList<>();
//		for (ProcessInstance pd : list) {
//			String processInstanceId = pd.getId();
//			BaseVO base = getBaseVOFromRunTask(processInstanceId);
//			if (userCode.equals(base.getCreateId())) {
//				base.setProcessInstance(pd);
//				base.setDeploymentId(pd.getDeploymentId());
//				processList.add(base);
//			}
//		}
//		return processList;
//	}
//
//	private BaseVO getBaseVOFromRunTask(String processInstanceId) {
//		BaseVO base = (BaseVO) getBaseVOFromRu_Variable(processInstanceId);
//		
//		Task task = null;
//		String candidateUserIds = "";
//		
//		List<Task> taskList = cusTaskService.findRunTaskByProcInstanceId(processInstanceId);
//		if (taskList != null && taskList.size() > 0) {
//			task = taskList.get(0);
//			candidateUserIds = cusTaskService.getAssignee(taskList);
//		}
//		System.out.println("ProcessServiceImpl getBaseVOFromRunTask candidateUserIds:" + candidateUserIds);
//		if(base != null) {
//			if (task != null) {
//				base.setTask(task);
//				base.setTaskDefinitionKey(task.getTaskDefinitionKey());
//				base.setToHandleTaskName(task.getName());
//				ProcessDefinitionCache.setRepositoryService(this.repositoryService);
//				ProcessDefinition process = ProcessDefinitionCache.get(task.getProcessDefinitionId());
//				if(process != null) {
//					base.setDescription(process.getDescription());
//					base.setProcessDefinition(process);
//				}
//			}
//			base.setAssign(candidateUserIds);
//			base.setProcessInstanceId(processInstanceId);
//		}
//		return base;
//	}

}