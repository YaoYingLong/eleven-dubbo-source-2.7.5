/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.router.tag;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.configcenter.ConfigChangeEvent;
import org.apache.dubbo.configcenter.ConfigChangeType;
import org.apache.dubbo.configcenter.ConfigurationListener;
import org.apache.dubbo.configcenter.DynamicConfiguration;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Constants;
import org.apache.dubbo.rpc.cluster.router.AbstractRouter;
import org.apache.dubbo.rpc.cluster.router.tag.model.TagRouterRule;
import org.apache.dubbo.rpc.cluster.router.tag.model.TagRuleParser;

import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.rpc.Constants.FORCE_USE_TAG;
import static org.apache.dubbo.rpc.cluster.Constants.TAG_KEY;

/**
 * TagRouter, "application.tag-router"
 */
public class TagRouter extends AbstractRouter implements ConfigurationListener {
    public static final String NAME = "TAG_ROUTER";
    private static final int TAG_ROUTER_DEFAULT_PRIORITY = 100;
    private static final Logger logger = LoggerFactory.getLogger(TagRouter.class);
    private static final String RULE_SUFFIX = ".tag-router";


    private TagRouterRule tagRouterRule;
    private String application;

    public TagRouter(DynamicConfiguration configuration, URL url) {
        super(configuration, url);
        this.priority = TAG_ROUTER_DEFAULT_PRIORITY;
    }

    @Override
    public synchronized void process(ConfigChangeEvent event) {
        if (logger.isDebugEnabled()) {
            logger.debug("Notification of tag rule, change type is: " + event.getChangeType() + ", raw rule is:\n " + event.getValue());
        }
        try {
            if (event.getChangeType().equals(ConfigChangeType.DELETED)) {
                this.tagRouterRule = null;
            } else {
                this.tagRouterRule = TagRuleParser.parse(event.getValue());
            }
        } catch (Exception e) {
            logger.error("Failed to parse the raw tag router rule and it will not take effect, please check if the rule matches with the template, the raw rule is:\n ", e);
        }
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public <T> List<Invoker<T>> route(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        if (CollectionUtils.isEmpty(invokers)) {
            return invokers;
        }
        final TagRouterRule tagRouterRuleCopy = tagRouterRule; // since the rule can be changed by config center, we should copy one to use.
        if (tagRouterRuleCopy == null || !tagRouterRuleCopy.isValid() || !tagRouterRuleCopy.isEnabled()) {
            return filterUsingStaticTag(invokers, url, invocation);
        }
        List<Invoker<T>> result = invokers;
        // 获取调用对象invocation中所设置的tag
        String tag = StringUtils.isEmpty(invocation.getAttachment(Constants.TAG_KEY)) ? url.getParameter(Constants.TAG_KEY) : invocation.getAttachment(Constants.TAG_KEY);
        if (StringUtils.isNotEmpty(tag)) { // 若请求具有特定标签的提供者
            List<String> addresses = tagRouterRuleCopy.getTagnameToAddresses().get(tag); // 获取对应tag所设置的服务提供者address
            if (CollectionUtils.isNotEmpty(addresses)) { // 首先按动态标签组过过滤
                // 根据tag所对应的address对所有服务提供者invokers进行过滤
                result = filterInvoker(invokers, invoker -> addressMatches(invoker.getUrl(), addresses));
                // 若过滤之后还有结果，那就用过滤之后的结果，若没有结果，但是此标签路由是要强制使用的，那么则会把空结果返回(没有此tag所对应的服务提供者可用)
                if (CollectionUtils.isNotEmpty(result) || tagRouterRuleCopy.isForce()) {
                    return result;
                }
            } else {// 动态标签组没有关于请求的应用程序的任何项目或它在过滤后为空，动态标签组但force=false，检查静态标签
                result = filterInvoker(invokers, invoker -> tag.equals(invoker.getUrl().getParameter(Constants.TAG_KEY)));
            }
            // 若没有可匹配当前标记请求的标记提供者，force.tag默认为false，这意味着它将调用任何没有标记的提供程序，除非它被明确禁止
            if (CollectionUtils.isNotEmpty(result) || isForceUseTag(invocation)) {
                return result;
            } else { // FAILOVER：返回没有任何标签的所有提供者。
                List<Invoker<T>> tmp = filterInvoker(invokers, invoker -> addressNotMatches(invoker.getUrl(), tagRouterRuleCopy.getAddresses()));
                return filterInvoker(tmp, invoker -> StringUtils.isEmpty(invoker.getUrl().getParameter(Constants.TAG_KEY)));
            }
        } else {
            // List<String> addresses = tagRouterRule.filter(providerApp);
            List<String> addresses = tagRouterRuleCopy.getAddresses(); // 返回动态标签组中的所有地址
            if (CollectionUtils.isNotEmpty(addresses)) {
                result = filterInvoker(invokers, invoker -> addressNotMatches(invoker.getUrl(), addresses));
                if (CollectionUtils.isEmpty(result)) {
                    return result; // 所有地址都在动态标签组中，返回空列表
                }
                // 如果有一些地址不在任何动态标签组中，则继续使用
            }
            return filterInvoker(result, invoker -> {
                String localTag = invoker.getUrl().getParameter(Constants.TAG_KEY);
                return StringUtils.isEmpty(localTag) || !tagRouterRuleCopy.getTagNames().contains(localTag);
            });
        }
    }

    /**
     * If there's no dynamic tag rule being set, use static tag in URL.
     * <p>
     * A typical scenario is a Consumer using version 2.7.x calls Providers using version 2.6.x or lower,
     * the Consumer should always respect the tag in provider URL regardless of whether a dynamic tag rule has been set to it or not.
     * <p>
     * TODO, to guarantee consistent behavior of interoperability between 2.6- and 2.7+, this method should has the same logic with the TagRouter in 2.6.x.
     *
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    private <T> List<Invoker<T>> filterUsingStaticTag(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        List<Invoker<T>> result = invokers;
        // Dynamic param
        String tag = StringUtils.isEmpty(invocation.getAttachment(Constants.TAG_KEY)) ? url.getParameter(Constants.TAG_KEY) : invocation.getAttachment(Constants.TAG_KEY);
        if (!StringUtils.isEmpty(tag)) { // Tag request
            result = filterInvoker(invokers, invoker -> tag.equals(invoker.getUrl().getParameter(TAG_KEY)));
            if (CollectionUtils.isEmpty(result) && !isForceUseTag(invocation)) {
                result = filterInvoker(invokers, invoker -> StringUtils.isEmpty(invoker.getUrl().getParameter(TAG_KEY)));
            }
        } else {
            result = filterInvoker(invokers, invoker -> StringUtils.isEmpty(invoker.getUrl().getParameter(TAG_KEY)));
        }
        return result;
    }

    @Override
    public boolean isRuntime() {
        return tagRouterRule != null && tagRouterRule.isRuntime();
    }

    @Override
    public boolean isForce() {
        // FIXME
        return tagRouterRule != null && tagRouterRule.isForce();
    }

    private boolean isForceUseTag(Invocation invocation) {
        return Boolean.valueOf(invocation.getAttachment(FORCE_USE_TAG, url.getParameter(FORCE_USE_TAG, "false")));
    }

    private <T> List<Invoker<T>> filterInvoker(List<Invoker<T>> invokers, Predicate<Invoker<T>> predicate) {
        return invokers.stream().filter(predicate).collect(Collectors.toList());
    }

    private boolean addressMatches(URL url, List<String> addresses) {
        return addresses != null && checkAddressMatch(addresses, url.getHost(), url.getPort());
    }

    private boolean addressNotMatches(URL url, List<String> addresses) {
        return addresses == null || !checkAddressMatch(addresses, url.getHost(), url.getPort());
    }

    private boolean checkAddressMatch(List<String> addresses, String host, int port) {
        for (String address : addresses) {
            try {
                if (NetUtils.matchIpExpression(address, host, port)) {
                    return true;
                }
                if ((ANYHOST_VALUE + ":" + port).equals(address)) {
                    return true;
                }
            } catch (UnknownHostException e) {
                logger.error("The format of ip address is invalid in tag route. Address :" + address, e);
            } catch (Exception e) {
                logger.error("The format of ip address is invalid in tag route. Address :" + address, e);
            }
        }
        return false;
    }

    public void setApplication(String app) {
        this.application = app;
    }

    @Override
    public <T> void notify(List<Invoker<T>> invokers) {
        if (CollectionUtils.isEmpty(invokers)) {
            return;
        }
        Invoker<T> invoker = invokers.get(0); // invoker表示一个服务执行者
        URL url = invoker.getUrl();
        // 要执行的服务在哪个应用上，服务提供者应用
        String providerApplication = url.getParameter(CommonConstants.REMOTE_APPLICATION_KEY);
        if (StringUtils.isEmpty(providerApplication)) { // 标签路由只能设置在某个应用上
            logger.error("TagRouter must getConfig from or subscribe to a specific application, but the application " +
                    "in this TagRouter is not specified.");
            return;
        }
        synchronized (this) {
            // 服务提供者所属的应用，application是TagRouter中的一个属性，表示当前TagRouter是在哪个应用上
            if (!providerApplication.equals(application)) {
                if (!StringUtils.isEmpty(application)) {
                    configuration.removeListener(application + RULE_SUFFIX, this);
                }
                // dubbo-demo-provider-application.tag-router
                String key = providerApplication + RULE_SUFFIX;
                configuration.addListener(key, this);
                application = providerApplication;
                String rawRule = configuration.getRule(key, DynamicConfiguration.DEFAULT_GROUP);
                if (StringUtils.isNotEmpty(rawRule)) {
                    this.process(new ConfigChangeEvent(key, rawRule));
                }
            }
        }
    }

}
