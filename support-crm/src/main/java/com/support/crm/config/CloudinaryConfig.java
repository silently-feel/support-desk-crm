package com.support.crm.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name:#{null}}")
    private String cloudName;

    @Value("${cloudinary.api-key:#{null}}")
    private String apiKey;

    @Value("${cloudinary.api-secret:#{null}}")
    private String apiSecret;

    @Value("${CLOUDINARY_URL:#{null}}")
    private String cloudinaryUrl;

    @Bean
    public Cloudinary cloudinary(){
        // If CLOUDINARY_URL is provided
        if(cloudinaryUrl != null && !cloudinaryUrl.isBlank()){
            return new Cloudinary(cloudinaryUrl);
        }
        // Map initialization with secure=true
        Map<String , Object> map = new HashMap<>();
        map.put("cloud_name" , cloudName);
        map.put("api_key",apiKey);
        map.put("api_secret" , apiSecret);
        map.put("secure" , true);

        return new Cloudinary(map);
    }
}
