package com.project.backend.handlers;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.graalvm.compiler.nodes.ReturnNode;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.data.spel.spi.Function;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

import com.project.backend.Configurations.GetTimeZone;
import com.project.backend.Model.*;

import com.project.backend.repositories.RequestRepository;
import com.project.backend.repositories.TagRepository;

import java.util.Set;
import java.util.Map.Entry;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.security.SecureRandom;
// 이제 controller 안에 덕지덕지 붙이는게 싫으니 따로 만들자
@Component
public class RequestHandler {

    // autowired가 스프링 4부터 이렇게 쓰면 자동으로 된다
    private final DatabaseClient databaseClient;
    private final RequestRepository requestRepository;
    private final TagRepository tagRepository;

    public RequestHandler(
        DatabaseClient databaseClient, 
        RequestRepository requestRepository,
        TagRepository tagRepository
    ) {
        this.databaseClient = databaseClient;
        this.requestRepository = requestRepository;
        this.tagRepository = tagRepository;
    }


    public Mono<ServerResponse> insert(ServerRequest req) {

    return ok().body(req.bodyToMono(RequestGetter.class).flatMap(map -> {
        return requestRepository.insertThenRetrunId(
            map.getCategory(), 
            map.getContext(),
            GetTimeZone.getSeoulDate(),
            map.getDeadline(),
            map.getHopeDate(),
            map.getUserId()).flatMap(requestId ->{
                for(int i =0 ; i < map.getTags().length; ++i) {
                    tagRepository.insertTag(map.getTags()[i], requestId).subscribe();
                }
                return Mono.just(requestId);
            });   
        }),Integer.class);
    }

    // 이 때는 tag 
    public Mono<ServerResponse> deleteWhenSuccessBidding(ServerRequest req) {
        
        


        return ok().body(null, Integer.class);
    }
    // bloc고쳐
    public Mono<ServerResponse> selectByCategory(ServerRequest req) {
        Flux<Request> rs = 
            databaseClient.execute("select * from request where category = :category")
            .bind("category", req.bodyToMono(Map.class).block().get("category"))
            .as(Request.class)
            .fetch()
            .all();
        return ok().body(rs, Request.class);
    }


    /*
    this.requestId = requestId;
        this.context = context;
        this.category = category;
        this.uploadAt = uploadAt;
        this.deadLine = deadLine;
        this.hopeDate = hopeDate;
        this.user_indexId = user_indexId;
    */

    public Mono<ServerResponse> selectAll(ServerRequest req) {
        Flux<Request> rs =
            databaseClient.execute("select r.* from request r order by r.deadLine desc")
            .fetch()
            .all()
            .flatMap(map -> {
                return Flux.just(
                 new Request(
                    (int)map.get("requestId"),
                     map.get("category").toString(),
                     map.get("context").toString(),
                     map.get("uploadAt").toString(),
                     map.get("deadLine").toString(),
                     map.get("hopeDate").toString(),
                     (int)map.get("user_indexId")
                ));
            });
            
        
        
        return ok().body(rs, Request.class);
    }

    @Transactional
    public Mono<ServerResponse> selectRequestsByTagContext(ServerRequest req) {
        Mono<String> mStr =  req.bodyToMono(String.class);
    
        return ok().body(mStr.flux().flatMap(str -> requestRepository
        .selectRequestsByTagContext(str)), Request.class);
    }

    public Mono<ServerResponse> deleteRequestWhenCancel(ServerRequest req) {
        
       

 
        return ok().body( req.bodyToMono(Integer.class)
        .flatMap(i -> {
            return requestRepository.updateTagWhenCancel(i)//  태그 업데이트된거 취소시키고(requestCount + 1 된것을 다시 되돌리고)
            .then(requestRepository.deleteTagRequestCountIsZero()) // 0이면 얘가 삽입되고 나서 zero가 된것
            .then(requestRepository.deleteReqHasTag(i))
            .then()
        }), Integer.class);
    }



}