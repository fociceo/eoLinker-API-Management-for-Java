package com.eolinker.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.eolinker.mapper.EnvFrontUriMapper;
import com.eolinker.mapper.EnvHeaderMapper;
import com.eolinker.mapper.EnvMapper;
import com.eolinker.mapper.EnvParamAdditionalMapper;
import com.eolinker.mapper.EnvParamMapper;
import com.eolinker.mapper.ProjectMapper;
import com.eolinker.mapper.ProjectOperationLogMapper;
import com.eolinker.pojo.Env;
import com.eolinker.pojo.EnvFrontUri;
import com.eolinker.pojo.EnvHeader;
import com.eolinker.pojo.EnvParam;
import com.eolinker.pojo.EnvParamAdditional;
import com.eolinker.pojo.ProjectOperationLog;
import com.eolinker.service.EnvService;

@Service
@Transactional(propagation=Propagation.REQUIRED,rollbackForClassName="java.lang.Exception")
public class EnvServiceImpl implements EnvService {

	@Autowired
	private EnvFrontUriMapper envFrontUriMapper;
	
	@Autowired
	private EnvHeaderMapper envHeaderMapper;
	
	@Autowired
	private EnvMapper envMapper;
	
	@Autowired
	private EnvParamMapper envParamMapper;
	
	@Autowired
	private EnvParamAdditionalMapper envParamAdditionalMapper;
	
	@Autowired
	private ProjectMapper ProjectMapper;
	
	@Autowired
	private ProjectOperationLogMapper projectOperationLogMapper;
	
	@Override
	public Map<String, Object> getEnvList(int projectID) {
		RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
		int userID = (int)requestAttributes.getAttribute("userID", RequestAttributes.SCOPE_SESSION);
		
		if(this.ProjectMapper.getProject(userID, projectID) == null)
			return null;
		
		Map<String,Object> resultMap = new HashMap<String, Object>();
		List<Map<String,Object>> resultList = new ArrayList<Map<String,Object>>();
		
		List<Env> envList = this.envMapper.getEnvList(projectID);
		
		if(envList != null && !envList.isEmpty()) {
			for(Env env : envList) {
				List<EnvFrontUri> envFrontUriList = this.envFrontUriMapper.getEnvFrontUriList(env.getEnvID());
				List<EnvHeader> envHeaderList = this.envHeaderMapper.getEnvHeaderList(env.getEnvID());
				List<EnvParam> envParamList = this.envParamMapper.getEnvParamList(env.getEnvID());
				List<EnvParamAdditional> envParamAdditional = this.envParamAdditionalMapper.getEnvParamAdditional(env.getEnvID());
	
				Map<String,Object> partialMap = new HashMap<String, Object>();
				partialMap.put("envID", env.getEnvID());
				partialMap.put("envName", env.getEnvName());
				partialMap.put("frontURIList", envFrontUriList);
				partialMap.put("headerList", envHeaderList);
				partialMap.put("paramList", envParamList);
				partialMap.put("additionalParamList", envParamAdditional);
				
				resultList.add(partialMap);
			}
			resultMap.put("envList", resultList);			
			return resultMap;
		} else
			return null;
	}
	
	

	
	@Override
	public int addEnv(int projectID, String envName, String frontUri, int applyProtocol, List<EnvHeader> headers, List<EnvParam> params, List<EnvParamAdditional> additionalParams)
							throws RuntimeException {
		RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
		int userID = (int)requestAttributes.getAttribute("userID", RequestAttributes.SCOPE_SESSION);
		
		// 检查项目是否存在
		if(this.ProjectMapper.getProject(userID, projectID) == null)
			return -1;
		
		// 新建环境
		int envID = -1;
		if(envName != null && !"".equals(envName)){
			Env env = new Env();
			env.setProjectID(projectID);
			env.setEnvName(envName);
			if(this.envMapper.addEnv(env) < 1)
				throw new RuntimeException("addEnv error");
			envID = env.getEnvID();
		} else
			return -1;
		
		// 新建前置URI
		if(frontUri != null && !"".equals(frontUri)) {
			EnvFrontUri envFrontUri = new EnvFrontUri();
			envFrontUri.setEnvID(envID);
			envFrontUri.setApplyProtocol(applyProtocol);
			envFrontUri.setUri(frontUri);
			if(this.envFrontUriMapper.addEnvFrontUri(envFrontUri) < 1)
				throw new RuntimeException("addEnvFrontUri error");
		}
		
		 // 新建请求头部
		if(headers != null && !headers.isEmpty()){
			for(EnvHeader header : headers) {
				header.setEnvID(envID);	
				if(this.envHeaderMapper.addEnvHeader(header) < 1)
					throw new RuntimeException("addEnvHeader error");
			}
		}
		
		 // 新建全局变量
		if(params != null && !params.isEmpty()){
			for(EnvParam param : params) {
				param.setEnvID(envID);	
				if(this.envParamMapper.addEnvParam(param) < 1)
					throw new RuntimeException("addEnvParam error");
			}
		}
		
		 // 新建额外参数
		if(additionalParams != null && !additionalParams.isEmpty()){
			for(EnvParamAdditional param : additionalParams) {
				param.setEnvID(envID);	
				if(this.envParamAdditionalMapper.addEnvParamAdditional(param) < 1)
					throw new RuntimeException("addEnvParamAdditional error");
			}
		}
		
		if(envID > 0) {
			ProjectOperationLog projectOperationLog = new ProjectOperationLog();
			projectOperationLog.setOpProjectID(projectID);
			projectOperationLog.setOpUerID(userID);
			projectOperationLog.setOpTarget(ProjectOperationLog.OP_TARGET_ENVIRONMENT);
			projectOperationLog.setOpTargetID(envID);
			projectOperationLog.setOpType(ProjectOperationLog.OP_TYPE_ADD);
			projectOperationLog.setOpDesc("添加环境:"+envName);
			
			this.projectOperationLogMapper.addProjectOperationLog(projectOperationLog);
		} else
			return -1;
		return envID;
	}




	@Override
	public int deleteEnv(int projectID, int envID) throws RuntimeException {
		RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
		int userID = (int)requestAttributes.getAttribute("userID", RequestAttributes.SCOPE_SESSION);
		
		// 检查项目是否存在
		if(this.ProjectMapper.getProject(userID, projectID) == null)
			return -1;
		Integer result = this.envMapper.checkEnvPermission(envID, userID);
		if(result == null || result < 0)
			return -1;
		String envName = this.envMapper.getEnvName(envID);
		
		// 删除旧的前置URI
		List<EnvFrontUri> envFrontUriList = this.envFrontUriMapper.getEnvFrontUriList(envID);
		if(envFrontUriList != null && !envFrontUriList.isEmpty()) {
			int affectedRow = this.envFrontUriMapper.deleteEnvFrontUri(envID);
			if(affectedRow < 1)
				throw new RuntimeException();
		}
		
		// 删除旧的请求头部
		List<EnvHeader> envHeaderList = this.envHeaderMapper.getEnvHeaderList(envID);
		if(envHeaderList != null && !envHeaderList.isEmpty()) {
			int affectedRow = this.envHeaderMapper.deleteEnvHeader(envID);
			if(affectedRow < 1)
				throw new RuntimeException();
		}
		
		// 删除旧的全局变量
		List<EnvParam> envParamList = this.envParamMapper.getEnvParamList(envID);
		if(envParamList != null && !envParamList.isEmpty()) {
			int affectedRow = this.envParamMapper.deleteEnvParam(envID);
			if(affectedRow < 1)
				throw new RuntimeException();		
		}
		
		// 删除额外参数
		List<EnvParamAdditional> envParamAdditional = this.envParamAdditionalMapper.getEnvParamAdditional(envID);
		if(envParamAdditional != null && !envParamAdditional.isEmpty()) {
			int affectedRow = this.envParamAdditionalMapper.deleteEnvParamAdditional(envID);
			if(affectedRow < 1)
				throw new RuntimeException();
		}
		
		int affectedRow = this.envMapper.deleteEnv(envID, projectID);

		if(affectedRow > 0) {
			ProjectOperationLog projectOperationLog = new ProjectOperationLog();
			projectOperationLog.setOpProjectID(projectID);
			projectOperationLog.setOpUerID(userID);
			projectOperationLog.setOpTarget(ProjectOperationLog.OP_TARGET_ENVIRONMENT);
			projectOperationLog.setOpTargetID(envID);
			projectOperationLog.setOpType(ProjectOperationLog.OP_TYPE_ADD);
			projectOperationLog.setOpDesc("添加环境:"+envName);
			//projectOperationLog.setOpTime(Timestamp.valueOf(new SimpleDateFormat("yyyy-MM-dd hh-mm-ss.fffffffff").format(new Date())));
			
			this.projectOperationLogMapper.addProjectOperationLog(projectOperationLog);
			
			return 1;
		} else
			throw new RuntimeException();
	}




	@Override
	public int editEnv(int envID, String envName, String frontUri, int applyProtocol, List<EnvHeader> headers,
			List<EnvParam> params, List<EnvParamAdditional> additionalParams) throws RuntimeException {
		RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
		int userID = (int)requestAttributes.getAttribute("userID", RequestAttributes.SCOPE_SESSION);
		
		Integer result = this.envMapper.checkEnvPermission(envID, userID);
		if(result == null || result < 0)
			return -1;
		
		Env env = new Env();
		env.setEnvID(envID);
		env.setEnvName(envName);
		
		if(this.envMapper.editEnv(env) < 1)
			throw new RuntimeException("editEnv error");
		this.envFrontUriMapper.deleteEnvFrontUri(envID);
		this.envHeaderMapper.deleteEnvHeader(envID);
		this.envParamMapper.deleteEnvParam(envID);
		this.envParamAdditionalMapper.deleteEnvParamAdditional(envID);
		// 新建前置URI
		if(frontUri != null && !"".equals(frontUri)) {
			EnvFrontUri envFrontUri = new EnvFrontUri();
			envFrontUri.setEnvID(envID);
			envFrontUri.setApplyProtocol(applyProtocol);
			envFrontUri.setUri(frontUri);
			if(this.envFrontUriMapper.addEnvFrontUri(envFrontUri) < 1)
				throw new RuntimeException("addEnvFrontUri error");
		}
		 // 新建请求头部
		if(headers != null && !headers.isEmpty()){
			for(EnvHeader header : headers) {
				header.setEnvID(envID);
				if(this.envHeaderMapper.addEnvHeader(header) < 1)
					throw new RuntimeException("addEnvHeader error");
			}
		}
		 // 新建全局变量
		if(params != null && !params.isEmpty()){
			for(EnvParam param : params) {
				param.setEnvID(envID);
				if(this.envParamMapper.addEnvParam(param) < 1)
					throw new RuntimeException("addEnvParam error");
			}
		}
		 // 新建额外参数
		if(additionalParams != null && !additionalParams.isEmpty()){
			for(EnvParamAdditional param : additionalParams) {
				param.setEnvID(envID);
				if(this.envParamAdditionalMapper.addEnvParamAdditional(param) < 1)
					throw new RuntimeException("addEnvParamAdditional error");
			}
		}
		return 1;
	}

	
	
	
	
	


}
