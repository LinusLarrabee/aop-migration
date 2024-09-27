
package com.tplink.shd.tauc.migration.annotation;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Description of this file
 *
 * @author sunhao
 * @version 1.0
 * @since 2024/3/7
 */
@Slf4j
@Aspect
@Component
public class InsertUniqueIdAspect {
    @Autowired
    ExecuteMigration aopMigrationProps;

    @Around("@annotation(InsertUniqueId)")
    public Object insertUniqueId(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("InsertUniqueId");
        try {
            Object[] args = joinPoint.getArgs();

            // 使用指定的入参部分生成唯一id，例如使用第一个和第二个参数的组合
            String param1 = args.length > 0 ? args[0].toString() : "";
            String param2 = args.length > 1 ? args[1].toString() : "";
//            String uniqueId = generateUniqueId(param1 + param2).substring(0, 32);
            String uniqueId = param1+"-"+param2;
            log.info(uniqueId);

            // 将唯一id直接写入到MDC
            MDC.put(aopMigrationProps.getUuid(), uniqueId);
            log.info("[InsertUniqueIdAspect] Generated unique id: {}", uniqueId);

            // 执行原方法
            return joinPoint.proceed();

        } catch (Exception e) {
            log.error("Error occurred in InsertUniqueIdAspect: ", e);
            return joinPoint.proceed();
        } finally {
            // 清除MDC中的数据
            MDC.remove(aopMigrationProps.getUuid());
        }
    }

    /**
     * 使用入参的第一个Object生成uuid
     */
    private int generateUUID(Object obj) throws IOException {
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos)){
            oos.writeObject(obj);
            oos.flush();
            byte[] bytes = baos.toByteArray();
            return Arrays.hashCode(bytes);
        }
    }

    /**
     * 根据String生成md5摘要
     * 后续可实现根据部分入参定制生成id的方式。
     */
    private String generateUniqueId(String input) throws NoSuchAlgorithmException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(input.getBytes());
        byte[] digest = md5.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append("0");
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
