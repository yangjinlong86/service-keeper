package org.nocoder.servicekeeper.application.service.impl;

import org.nocoder.servicekeeper.application.assembler.DeploymentPlanAssembler;
import org.nocoder.servicekeeper.application.assembler.ServerServiceMappingAssembler;
import org.nocoder.servicekeeper.application.dto.DeploymentPlanDto;
import org.nocoder.servicekeeper.application.dto.ServerServiceMappingDto;
import org.nocoder.servicekeeper.application.observer.DeploymentLogObserver;
import org.nocoder.servicekeeper.application.observer.DeploymentMessage;
import org.nocoder.servicekeeper.application.observer.DeploymentSubject;
import org.nocoder.servicekeeper.application.service.DeploymentService;
import org.nocoder.servicekeeper.common.enumeration.ServiceStatus;
import org.nocoder.servicekeeper.common.ssh.Certification;
import org.nocoder.servicekeeper.common.ssh.SshClient;
import org.nocoder.servicekeeper.common.util.DateTimeUtils;
import org.nocoder.servicekeeper.domain.modal.DeploymentLog;
import org.nocoder.servicekeeper.domain.modal.Server;
import org.nocoder.servicekeeper.domain.modal.ServerServiceMapping;
import org.nocoder.servicekeeper.infrastructure.repository.DeploymentLogRepository;
import org.nocoder.servicekeeper.infrastructure.repository.ServerRepository;
import org.nocoder.servicekeeper.infrastructure.repository.ServerServiceMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jason
 * @date 2019/4/17.
 */
@Service
public class DeploymentServiceImpl implements DeploymentService {
    private Logger logger = LoggerFactory.getLogger(DeploymentServiceImpl.class);
    private List<ServerServiceMappingDto> mappingDtoCacheList = Collections.emptyList();
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private ServerRepository serverRepository;
    @Resource
    private ServerServiceMappingRepository repository;
    @Resource
    private ServerServiceMappingAssembler assembler;
    @Resource
    private DeploymentLogRepository deploymentLogRepository;
    @Resource
    private DeploymentPlanAssembler deploymentPlanAssembler;

    @Value("${service-keeper.deployment-log-path}")
    private String deploymentLogPath;

    @Override
    public void executeCommand(Integer serviceId, Integer serverId, List<String> commandList, String operationType) throws Exception {
        // create deployment log
        deploymentLogRepository.add(createDeploymentLog(serviceId, serverId, operationType));
        // get latest log id
        Integer logId = deploymentLogRepository.getDeploymentLogId(serviceId, serverId);
        // get logfile path from yml config
        String directory = deploymentLogPath;
        String fileName = "deployment-" + logId + "-" + DateTimeUtils.getCurrentDate() + ".log";
        // update log file path
        deploymentLogRepository.updateLogFilePath(logId, directory + fileName);
        threadPoolExecutor.execute(() -> {
            Server server = serverRepository.getById(serverId);
            logger.info("start to execute deploymentMessage...");
            Certification certification = getCertification(server);
            List<String> resultList = SshClient.execCommands(certification, commandList);

            DeploymentSubject subject = new DeploymentSubject();
            new DeploymentLogObserver(subject);
            DeploymentMessage message = new DeploymentMessage();
            message.setLogFileDirectory(directory);
            message.setLogFileName(fileName);
            message.setServiceId(serviceId);
            message.setServerId(serverId);
            message.setCommandList(commandList);
            message.setResultList(resultList);
            subject.setDeploymentMessage(message);
            logger.info("execute deploymentMessage finished!");
        });
    }

    private DeploymentLog createDeploymentLog(Integer serviceId, Integer serverId, String operationType) {
        DeploymentLog log = new DeploymentLog();
        log.setServiceId(serviceId);
        log.setServerId(serverId);
        log.setOperation(operationType);
        // TODO get current user id
        log.setOperator("");
        log.setCreateTime(DateTimeUtils.getCurrentDateTime());
        return log;
    }

    private Certification getCertification(Server server) {
        Certification certification = new Certification();
        certification.setHost(server.getIp());
        certification.setPort(Integer.parseInt(server.getPort()));
        certification.setUser(server.getUser());
        certification.setPassword(server.getPassword());
        return certification;
    }


    @Override
    public int add(ServerServiceMappingDto dto) {
        dto.setServiceStatus(ServiceStatus.STOPPED.status());
        dto.setCreateTime(DateTimeUtils.getCurrentDateTime());
        ServerServiceMapping mapping = assembler.convertToMapping(dto);
        int res = repository.insert(mapping);
        if (res > 0) {
            reloadMappingDtoCacheList();
        }
        return res;
    }

    @Override
    public int update(ServerServiceMappingDto dto) {
        dto.setUpdateTime(DateTimeUtils.getCurrentDateTime());
        ServerServiceMapping mapping = assembler.convertToMapping(dto);
        int res = repository.update(mapping);
        if(res>0){
            reloadMappingDtoCacheList();
        }
        return res;
    }

    @Override
    public int updateServiceStatus(Integer serverId, Integer serviceId, String serviceStatus) {
        int res = repository.updateServiceStatus(serverId, serviceId, serviceStatus);
        if (res > 0) {
            reloadMappingDtoCacheList();
        }
        return res;
    }

    @Override
    public int delete(Integer id) {
        int res = repository.delete(id);
        if (res > 0) {
            reloadMappingDtoCacheList();
        }
        return res;
    }

    @Override
    public List<ServerServiceMappingDto> getAll() {
        if (CollectionUtils.isEmpty(this.mappingDtoCacheList)) {
            reloadMappingDtoCacheList();
        }
        return this.mappingDtoCacheList;
    }

    @Override
    public ServerServiceMappingDto getById(Integer id) {
        return assembler.convertToDto(repository.getById(id));
    }

    @Override
    public List<ServerServiceMappingDto> getByServiceId(Integer serviceId) {
        return assembler.convertToDtoList(repository.getByServiceId(serviceId));
    }

    @Override
    public List<ServerServiceMappingDto> getByServerId(Integer serverId) {
        return assembler.convertToDtoList(repository.getByServerId(serverId));
    }

    @Override
    public ServerServiceMappingDto getByServerIdAndServiceId(Integer serverId, Integer serviceId) {
        return assembler.convertToDto(repository.getByServerIdAndServiceId(serverId, serviceId));
    }

    @Override
    public List<DeploymentPlanDto> getDeploymentPlans() {
        return deploymentPlanAssembler.convertToDtoList(repository.getDeploymentPlans());
    }

    private void reloadMappingDtoCacheList(){
        this.mappingDtoCacheList = assembler.convertToDtoList(repository.getAll());
    }
}
