package it.eng.dome.revenue.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.Module;

import it.eng.dome.brokerage.utils.enumappers.TMF637EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF678EnumModule;

@Configuration
public class JacksonModuleConfig {

	// TMF637EnumModule handles ProductStatusType enum mapping
 	@Bean
 	public Module getTmf637EnumModule() {
        return new TMF637EnumModule();
    }
 	
    // TMF678EnumModule handles State enum mapping
    @Bean
    public Module getTmf678EnumModule() {
        return new TMF678EnumModule();
    }

}
