package it.eng.dome.brokerage.utils.enumappers;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;

class ProductStatusTypeDeserializer extends StdDeserializer<ProductStatusType> {

		private static final Logger logger = LoggerFactory.getLogger(ProductStatusTypeDeserializer.class);

	public ProductStatusTypeDeserializer() {
		super(ProductStatusType.class);
		logger.debug("******* deserializer constructor");
	}

	@Override
	public ProductStatusType deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		logger.debug("******* deserializing");
		return ProductStatusType.fromValue(jp.getText());
	}

}

class ProductStatusTypeSerializer extends StdSerializer<ProductStatusType> {

	private static final Logger logger = LoggerFactory.getLogger(ProductStatusTypeSerializer.class);

	public ProductStatusTypeSerializer() {
		super(ProductStatusType.class);
		logger.debug("***** constructor");
	}

	@Override
	public void serialize(ProductStatusType value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
		logger.debug("****** serializing");
		jgen.writeString(value.getValue());
	}
}

public class TMF637EnumModule extends SimpleModule {

    public TMF637EnumModule() {
        super(TMF637EnumModule.class.getName());
		this.addDeserializer(ProductStatusType.class, new ProductStatusTypeDeserializer());
		this.addSerializer(ProductStatusType.class, new ProductStatusTypeSerializer());
    }

}
