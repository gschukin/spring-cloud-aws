/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.aws.cache.config.xml;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.cloud.aws.cache.SimpleSpringMemcached;
import org.springframework.cloud.aws.context.config.xml.GlobalBeanDefinitionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

import java.util.List;

import static org.springframework.cloud.aws.core.config.xml.XmlWebserviceConfigurationUtils.getCustomClientOrDefaultClientBeanName;

/**
 * Parser for the {@code <aws-cache:cache-manager />} element.
 *
 * @author Alain Sahli
 * @author Agim Emruli
 * @since 1.0
 */
class CacheBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	private static final String CACHE_MANAGER = "cacheManager";
	private static final String CACHE_CLUSTER_ELEMENT_NAME = "cache-cluster";
	private static final String CACHE_REF_ELEMENT_NAME = "cache-ref";
	private static final String CACHE_ELEMENT_NAME = "cache";

	private static final String ELASTICACHE_MEMCACHE_CLIENT_FACTORY_BEAN = "org.springframework.cloud.aws.cache.ElastiCacheMemcachedFactoryBean";
	private static final String MEMCACHE_CLIENT_CLASS_NAME = "org.springframework.cloud.aws.cache.StaticMemcachedFactoryBean";
	private static final String ELASTI_CACHE_CLIENT_CLASS_NAME = "com.amazonaws.services.elasticache.AmazonElastiCacheClient";

	@Override
	protected String getBeanClassName(Element element) {
		return "org.springframework.cache.support.SimpleCacheManager";
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		if (parserContext.getRegistry().containsBeanDefinition(CACHE_MANAGER)) {
			parserContext.getReaderContext().error("Only one cache manager can be defined", element);
		}

		builder.addPropertyValue("caches", createCacheCollection(element, parserContext));
	}

	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) throws BeanDefinitionStoreException {
		return CACHE_MANAGER;
	}

	private ManagedList<Object> createCacheCollection(Element element, ParserContext parserContext) {
		ManagedList<Object> caches = new ManagedList<>();
		List<Element> cacheElements = DomUtils.getChildElements(element);

		for (Element cacheElement : cacheElements) {
			String elementName = cacheElement.getLocalName();

			switch (elementName) {
				case CACHE_REF_ELEMENT_NAME:
					caches.add(new RuntimeBeanReference(cacheElement.getAttribute("ref")));
					break;
				case CACHE_CLUSTER_ELEMENT_NAME:
					String cacheClusterId = getRequiredAttribute("name", cacheElement, parserContext);
					caches.add(createCache(cacheClusterId, createElastiCacheFactoryBean(cacheElement, parserContext,
							cacheClusterId), cacheElement.getAttribute("expiration")));
					break;
				case CACHE_ELEMENT_NAME:
					String name = getRequiredAttribute("name", cacheElement, parserContext);
					String address = getRequiredAttribute("address", cacheElement, parserContext);
					caches.add(createCache(name, createStaticMemcachedFactoryBean(address), cacheElement.getAttribute("expiration")));
					break;
				default:
					parserContext.getReaderContext().error("Unknown element detected",parserContext.extractSource(cacheElement));
			}
		}
		return caches;
	}

	private static String getRequiredAttribute(String attributeName, Element source, ParserContext parserContext) {
		if (StringUtils.hasText(source.getAttribute(attributeName))) {
			return source.getAttribute(attributeName);
		} else {
			parserContext.getReaderContext().error("Attribute '" + attributeName + "' is required", source);
			return null;
		}
	}

	private static BeanDefinition createCache(String name, BeanDefinition memCachedClient, String expiration) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SimpleSpringMemcached.class);
		beanDefinitionBuilder.addConstructorArgValue(memCachedClient);
		beanDefinitionBuilder.addConstructorArgValue(name);
		if (StringUtils.hasText(expiration)) {
			beanDefinitionBuilder.addPropertyValue("expiration", expiration);
		}
		return beanDefinitionBuilder.getBeanDefinition();
	}

	private static BeanDefinition createStaticMemcachedFactoryBean(String address) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(MEMCACHE_CLIENT_CLASS_NAME);
		beanDefinitionBuilder.addConstructorArgValue(address);
		return beanDefinitionBuilder.getBeanDefinition();
	}

	private static BeanDefinition createElastiCacheFactoryBean(Element source, ParserContext parserContext, String clusterId) {
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ELASTICACHE_MEMCACHE_CLIENT_FACTORY_BEAN);
		beanDefinitionBuilder.addConstructorArgReference(getCustomClientOrDefaultClientBeanName(source, parserContext,
				"amazon-elasti-cache", ELASTI_CACHE_CLIENT_CLASS_NAME));
		beanDefinitionBuilder.addConstructorArgValue(clusterId);
		beanDefinitionBuilder.addConstructorArgReference(GlobalBeanDefinitionUtils.retrieveResourceIdResolverBeanName(parserContext.getRegistry()));
		return beanDefinitionBuilder.getBeanDefinition();
	}
}
