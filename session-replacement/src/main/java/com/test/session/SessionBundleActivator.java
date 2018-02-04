package com.test.session;

import java.util.Hashtable;

import javax.servlet.Filter;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.test.session.servlet.filters.SessionManagementFilter;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SessionBundleActivator implements BundleActivator {
    private ServiceRegistration registration;

    public void start(BundleContext context) throws Exception {
        Hashtable props = new Hashtable();

        props.put("osgi.http.whiteboard.filter.regex", "/.*");
        //props.put("osgi.http.whiteboard.context.select", "Hello World!");

        props.put("service.ranking", Integer.MAX_VALUE);
        props.put("filter.init..message", "Hello World!");

        // this.registration = context.registerService(Filter.class.getName(), new SessionManagementFilter(), props);
    }

    public void stop(BundleContext context) throws Exception {
        //this.registration.unregister();
    }
}