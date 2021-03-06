package org.nocoder.servicekeeper.infrastructure.repository;

import org.apache.ibatis.annotations.*;
import org.nocoder.servicekeeper.domain.modal.Server;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface ServerRepository {


    @Select("SELECT * FROM server")
    List<Server> getAll();

    @Select("SELECT * FROM server WHERE id = #{id}")
    Server getById(Integer id);

    @Insert("INSERT INTO server(name,ip,port,protocol,user,password) VALUES(#{name}, #{ip}, #{port}, #{protocol},#{user}, #{password})")
    int insert(Server server);

    @Update("UPDATE server SET name=#{name}, ip=#{ip}, port=#{port}, user=#{user}, password=#{password} WHERE id =#{id}")
    int update(Server server);

    @Delete("DELETE FROM server WHERE id =#{id}")
    int delete(Integer id);

}
