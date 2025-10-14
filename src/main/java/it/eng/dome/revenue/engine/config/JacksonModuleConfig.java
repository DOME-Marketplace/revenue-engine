package it.eng.dome.revenue.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.Module;

import it.eng.dome.brokerage.utils.enumappers.TMF620EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF622EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF632EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF637EnumModule;
import it.eng.dome.brokerage.utils.enumappers.TMF678EnumModule;


@Configuration
public class JacksonModuleConfig {
	
    @Bean
    public Module getTmf620EnumModule() {
		return new TMF620EnumModule();
	}
	
 	// TMF622EnumModule handles ProductOrderStateType, ProductOrderItemStateType, OrderItemActionType, ProductStatusType, TaskStateType enums mapping
    @Bean
 	public Module getTmf622EnumModule() {
        return new TMF622EnumModule();
    }
    
    
    @Bean
 	public Module getTmf632EnumModule() {
        return new TMF632EnumModule();
    }
    
	// TMF637EnumModule handles ProductStatusType enum mapping
 	@Bean
 	public Module getTmf637EnumModule() {
        return new TMF637EnumModule();
    }

    @Bean
    public Module getTmf678EnumModule() {
		return new TMF678EnumModule();
	}
    


}
