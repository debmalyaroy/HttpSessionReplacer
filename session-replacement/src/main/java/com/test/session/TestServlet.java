package com.test.session;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SlingServlet(paths = "/bin/testservlet", extensions = "json", methods = "GET")
public class TestServlet extends SlingSafeMethodsServlet {
    private static final long serialVersionUID = 4233724958062534730L;
    private static final Logger LOGGER = LoggerFactory.getLogger(TestServlet.class);

    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        LOGGER.debug("Inside the servlet");

        HttpSession session = request.getSession(false);

        if (session == null) {
            LOGGER.debug("No session was present.");
            session = request.getSession();
        }

        LOGGER.debug("Session ID: {}", session.getId());
        LOGGER.debug("Attribute already set : " + session.getAttribute("XYZ"));

        session.setAttribute("Test", "Test1");
        session.setAttribute("XYZ", ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        LOGGER.debug("Attribute set : " + session.getAttribute("Test"));

        session.setAttribute("Test", "Test2");
        LOGGER.debug("Attribute updated : " + session.getAttribute("Test"));

        session.removeAttribute("Test");
        LOGGER.debug("Attribute removed : " + session.getAttribute("Test"));

        response.getWriter().append("Session created. Session details: " + session.toString());
    }
}
