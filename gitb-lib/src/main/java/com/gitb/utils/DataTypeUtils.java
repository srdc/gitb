package com.gitb.utils;

import com.gitb.core.AnyContent;
import com.gitb.core.ValueEmbeddingEnumeration;
import com.gitb.types.DataType;
import com.gitb.types.DataTypeFactory;
import com.gitb.types.ListType;
import com.gitb.types.MapType;
import com.sun.jersey.api.client.Client;
import org.apache.commons.codec.binary.Base64;

import javax.ws.rs.core.MediaType;
import java.util.Map;

/**
 * Created by serbay.
 */
public class DataTypeUtils {

	public static DataType convertAnyContentToDataType(AnyContent anyContent) {
		DataType data = DataTypeFactory.getInstance().create(anyContent.getType());

		setDataTypeValueWithAnyContent(data, anyContent);

		return data;
	}

	public static AnyContent convertDataTypeToAnyContent(String name, DataType fragment) {

		AnyContent attachment = new AnyContent();
		attachment.setName(name);
		attachment.setType(fragment.getType());

		switch (fragment.getType()) {
			case DataType.OBJECT_DATA_TYPE:
			case DataType.SCHEMA_DATA_TYPE:
			case DataType.BINARY_DATA_TYPE: {
				byte[] value = Base64.encodeBase64(fragment.serializeByDefaultEncoding());

				attachment.setEmbeddingMethod(ValueEmbeddingEnumeration.BASE_64);
				attachment.setValue(new String(value));
				break;
			}
			case DataType.BOOLEAN_DATA_TYPE:
			case DataType.NUMBER_DATA_TYPE:
			case DataType.STRING_DATA_TYPE: {
				byte[] value = fragment.serializeByDefaultEncoding();

				attachment.setEmbeddingMethod(ValueEmbeddingEnumeration.STRING);
				attachment.setValue(new String(value));
				break;
			}
			case DataType.LIST_DATA_TYPE: {
				ListType listFragment = (ListType) fragment;

				for (int i = 0; i < listFragment.getSize(); i++) {
					DataType item = listFragment.getItem(i);
					AnyContent content = convertDataTypeToAnyContent(null, item);

					attachment.getItem().add(content);
				}
				break;
			}
			case DataType.MAP_DATA_TYPE:
				Map<String, DataType> values = (Map<String, DataType>) ((MapType)fragment).getValue();

				for (Map.Entry<String, DataType> entry : values.entrySet()) {
					AnyContent content = convertDataTypeToAnyContent(entry.getKey(), entry.getValue());

					attachment.getItem().add(content);
				}
				break;
		}

		return attachment;
	}

	public static void setContentValueWithDataType(AnyContent content, DataType data) {
		AnyContent value = convertDataTypeToAnyContent(null, data);
		content.setEmbeddingMethod(value.getEmbeddingMethod());
		content.setType(value.getType());
		content.setValue(value.getValue());
	}

	public static void setDataTypeValueWithAnyContent(DataType data, AnyContent anyContent) {
		switch (anyContent.getType()) {
			case DataType.LIST_DATA_TYPE: {
				ListType list = (ListType) data;

				for(AnyContent childItem : anyContent.getItem()) {
					DataType childData = convertAnyContentToDataType(childItem);

					if(list.getContainedType() == null) {
						list.setContainedType(childData.getType());
					}

					list.append(childData);
				}
				break;
			}
			case DataType.MAP_DATA_TYPE: {
				MapType map = (MapType) data;

				for(AnyContent childItem : anyContent.getItem()) {
					DataType childData = convertAnyContentToDataType(childItem);

					map.addItem(childItem.getName(), childData);
				}

				break;
			}
			default: {

				switch (anyContent.getEmbeddingMethod()) {
					case STRING: {
						data.deserialize(anyContent.getValue().getBytes());
						break;
					}
					case BASE_64: {
						data.deserialize(Base64.decodeBase64(
                                EncodingUtils.extractBase64FromDataURL(anyContent.getValue())));
                        System.out.println(Base64.decodeBase64(
                                EncodingUtils.extractBase64FromDataURL(anyContent.getValue())));
                        break;
					}
					case URI: {

						Client client = Client.create();
						String content =
							client
								.resource(anyContent.getValue())
								.accept(MediaType.WILDCARD_TYPE)
								.get(String.class);

						data.deserialize(content.getBytes());
						break;
					}
				}

				break;
			}
		}
	}
}
