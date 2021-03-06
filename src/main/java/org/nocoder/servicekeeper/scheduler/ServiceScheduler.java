package org.nocoder.servicekeeper.scheduler;

import org.apache.commons.lang3.StringUtils;
import org.nocoder.servicekeeper.application.dto.ServerDto;
import org.nocoder.servicekeeper.application.dto.ServerServiceMappingDto;
import org.nocoder.servicekeeper.application.dto.ServiceDto;
import org.nocoder.servicekeeper.application.service.DeploymentService;
import org.nocoder.servicekeeper.application.service.ServerService;
import org.nocoder.servicekeeper.application.service.ServiceService;
import org.nocoder.servicekeeper.common.enumeration.ServiceStatus;
import org.nocoder.servicekeeper.common.ssh.Certification;
import org.nocoder.servicekeeper.common.ssh.SshClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jason
 * @date 2019/4/26.
 */
@Component
@EnableScheduling
public class ServiceScheduler {

    private Logger logger = LoggerFactory.getLogger(ServiceScheduler.class);

    @Resource
    private DeploymentService deploymentService;
    @Resource
    private ServiceService serviceService;
    @Resource
    private ServerService serverService;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Scheduled(fixedDelay = 30000)
    public void checkAndUpdateServiceStatus() {
        logger.info("scheduled task [checkAndUpdateServiceStatus] start.");
        List<ServerServiceMappingDto> ssmDtoList = deploymentService.getAll();
        ssmDtoList.forEach(ssmDto -> {
            // get server
            ServerDto server = serverService.getById(ssmDto.getServerId());
            // create cert
            Certification cert = createCertification(server);
            // get service
            ServiceDto service = serviceService.getById(ssmDto.getServiceId());
            // exec command
            List<String> result = SshClient.execCommands(cert, Arrays.asList("docker ps | grep " + service.getDockerContainerName()));
            ServerServiceMappingDto ssMappingDto = deploymentService.getByServerIdAndServiceId(ssmDto.getServerId(), ssmDto.getServiceId());
            if (CollectionUtils.isEmpty(result) || result.size()<=1) {
                if(!ssMappingDto.getServiceStatus().equals(ServiceStatus.LOST_CONNECTION.status())){
                    deploymentService.updateServiceStatus(ssmDto.getServerId(), ssmDto.getServiceId(), ServiceStatus.LOST_CONNECTION.status());
                }
                logger.debug("can not connection to {} - {} service", service.getName(), server.getIp());
            } else if (StringUtils.isBlank(result.get(1))) {
                // when the command `docker ps | grep xxx` return an empty string, update the service status to stopped
                if(!ssMappingDto.getServiceStatus().equals(ServiceStatus.STOPPED.status())){
                    deploymentService.updateServiceStatus(ssmDto.getServerId(), ssmDto.getServiceId(), ServiceStatus.STOPPED.status());
                }
                logger.debug("{} - {} service status is stopped.", service.getName(), server.getIp());
            } else {
                // when the command `docker ps | grep xxx` return an not empty string, update service status to running
                if(!ssMappingDto.getServiceStatus().equals(ServiceStatus.RUNNING.status())){
                    deploymentService.updateServiceStatus(ssmDto.getServerId(), ssmDto.getServiceId(), ServiceStatus.RUNNING.status());
                }
                logger.debug("update {} - {} service status to `running`", service.getName(), server.getIp());
            }
        });
        logger.info("scheduled task [checkAndUpdateServiceStatus] end.");
    }

    private Certification createCertification(ServerDto server) {
        Certification cert = new Certification();
        cert.setHost(server.getIp());
        cert.setPort(Integer.parseInt(server.getPort()));
        cert.setUser(server.getUser());
        cert.setPassword(server.getPassword());
        return cert;
    }
}
