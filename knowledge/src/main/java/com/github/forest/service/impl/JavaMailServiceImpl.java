package com.github.forest.service.impl;

import com.github.forest.service.JavaMailService;
import com.github.forest.service.UserService;
import com.github.forest.util.PasswordEncoder;
import com.github.forest.util.Utils;
import com.sun.mail.util.MailSSLSocketFactory;
import org.apache.commons.lang.time.StopWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.Resource;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @Author sunzy
 * @Date 2023/5/28 20:18
 */
@Service
public class JavaMailServiceImpl implements JavaMailService {

    @Resource
    private JavaMailSenderImpl mailSender;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserService userService;
    /**
     * thymeleaf模板引擎
     */
    @Resource
    private TemplateEngine templateEngine;

    @Value("${spring.mail.host}")
    private String SERVER_HOST = "smtp.qq.com";
    @Value("${spring.mail.port}")
    private String SERVER_PORT;
    @Value("${spring.mail.username}")
    private String USERNAME;
    @Value("${spring.mail.password}")
    private String PASSWORD;
    @Value("${resource.domain}")
    private String BASE_URL;



    @Override
    public Integer sendEmailCode(String email) throws MessagingException {
        try {
            return sendCode(email, 0);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer sendForgetPasswordEmail(String email) throws MessagingException {
        try {
            return sendCode(email, 1);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }


    private Integer sendCode(String to, Integer type) throws MessagingException, GeneralSecurityException {
        Properties props = new Properties();
        // 表示SMTP发送邮件，需要进行身份验证
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.ssl.enable", true);
        props.put("mail.smtp.host", SERVER_HOST);
        props.put("mail.smtp.port", SERVER_PORT);
        // 如果使用ssl，则去掉使用25端口的配置，进行如下配置,
        MailSSLSocketFactory sf = new MailSSLSocketFactory();
        props.put("mail.smtp.socketFactory.class", sf);
        props.put("mail.smtp.socketFactory.port", SERVER_PORT);
        // 发件人的账号，填写控制台配置的发信地址,比如xxx@xxx.com
        props.put("mail.user", USERNAME);
        // 访问SMTP服务时需要提供的密码(在控制台选择发信地址进行设置)
        props.put("mail.password", PASSWORD);
        mailSender.setJavaMailProperties(props);
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(USERNAME);
        simpleMailMessage.setTo(to);
        if (type == 0) {
            Integer code = Utils.genCode();
            stringRedisTemplate.opsForValue().set(to, String.valueOf(code), 5 * 60, TimeUnit.SECONDS);
//            redisService.set(to, code, 5 * 60);
            simpleMailMessage.setSubject("新用户注册邮箱验证");
            simpleMailMessage.setText("【RYMCU】您的校验码是 " + code + ",有效时间 5 分钟，请不要泄露验证码给其他人。如非本人操作,请忽略！");
            mailSender.send(simpleMailMessage);
            return 1;
        } else if (type == 1) {
            String code = PasswordEncoder.encode(to);
            String url = BASE_URL + "/forget-password?code=" + code;
            stringRedisTemplate.opsForValue().set(code, to, 15 * 60, TimeUnit.SECONDS);

            String thymeleafTemplatePath = "mail/forgetPasswordTemplate";
            Map<String, Object> thymeleafTemplateVariable = new HashMap<String, Object>(1);
            thymeleafTemplateVariable.put("url", url);

            sendTemplateEmail(USERNAME,
                    new String[]{to},
                    new String[]{},
                    "【RYMCU】 找回密码",
                    thymeleafTemplatePath,
                    thymeleafTemplateVariable);
            return 1;
        }
        return 0;
    }

    /**
     * 发送thymeleaf模板邮件
     *
     * @param deliver                   发送人邮箱名 如： javalsj@163.com
     * @param receivers                 收件人，可多个收件人 如：11111@qq.com,2222@163.com
     * @param carbonCopys               抄送人，可多个抄送人 如：33333@sohu.com
     * @param subject                   邮件主题 如：您收到一封高大上的邮件，请查收。
     * @param thymeleafTemplatePath     邮件模板 如：mail\mailTemplate.html。
     * @param thymeleafTemplateVariable 邮件模板变量集
     */
    public void sendTemplateEmail(String deliver, String[] receivers, String[] carbonCopys, String subject, String thymeleafTemplatePath,
                                  Map<String, Object> thymeleafTemplateVariable) throws MessagingException {
        String text = null;
        if (thymeleafTemplateVariable != null && thymeleafTemplateVariable.size() > 0) {
            Context context = new Context();
            thymeleafTemplateVariable.forEach((key, value) -> context.setVariable(key, value));
            text = templateEngine.process(thymeleafTemplatePath, context);
        }
        sendMimeMail(deliver, receivers, carbonCopys, subject, text, true, null);
    }

    /**
     * 发送的邮件(支持带附件/html类型的邮件)
     *
     * @param deliver             发送人邮箱名 如： javalsj@163.com
     * @param receivers           收件人，可多个收件人 如：11111@qq.com,2222@163.com
     * @param carbonCopys         抄送人，可多个抄送人 如：3333@sohu.com
     * @param subject             邮件主题 如：您收到一封高大上的邮件，请查收。
     * @param text                邮件内容 如：测试邮件逗你玩的。 <html><body><img
     *                            src=\"cid:attchmentFileName\"></body></html>
     * @param attachmentFilePaths 附件文件路径 如：
     *                            需要注意的是addInline函数中资源名称attchmentFileName需要与正文中cid:attchmentFileName对应起来
     * @throws Exception 邮件发送过程中的异常信息
     */
    private void sendMimeMail(String deliver, String[] receivers, String[] carbonCopys, String subject, String text,
                              boolean isHtml, String[] attachmentFilePaths) throws MessagingException {
        StopWatch stopWatch = new StopWatch();

        stopWatch.start();
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
        helper.setFrom(deliver);
        helper.setTo(receivers);
        helper.setCc(carbonCopys);
        helper.setSubject(subject);
        helper.setText(text, isHtml);
        // 添加邮件附件
        if (attachmentFilePaths != null && attachmentFilePaths.length > 0) {
            for (String attachmentFilePath : attachmentFilePaths) {
                File file = new File(attachmentFilePath);
                if (file.exists()) {
                    String attachmentFile = attachmentFilePath
                            .substring(attachmentFilePath.lastIndexOf(File.separator));
                    long size = file.length();
                    if (size > 1024 * 1024) {
                        String msg = String.format("邮件单个附件大小不允许超过1MB，[%s]文件大小[%s]。", attachmentFilePath,
                                file.length());
                        throw new RuntimeException(msg);
                    } else {
                        FileSystemResource fileSystemResource = new FileSystemResource(file);
                        helper.addInline(attachmentFile, fileSystemResource);
                    }
                }
            }
        }
        mailSender.send(mimeMessage);
        stopWatch.stop();

    }

}