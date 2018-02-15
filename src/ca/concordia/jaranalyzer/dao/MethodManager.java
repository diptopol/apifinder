package ca.concordia.jaranalyzer.dao;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import ca.concordia.jaranalyzer.model.Method;

public class MethodManager {
	protected SessionFactory sessionFactory;
	 
	public MethodManager(SessionFactory sessionFactory){   
    	//setup();
		this.sessionFactory = sessionFactory;
	}
	
    public void setup() {
//    	final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
//    	        .configure() // configures settings from hibernate.cfg.xml
//    	        .build();
//    	try {
//    	    sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
//    	} catch (Exception ex) {
//    	    StandardServiceRegistryBuilder.destroy(registry);
//    	}
    	sessionFactory = new Configuration().configure("hibernate.cfg.xml").buildSessionFactory();
    }
 
    public void exit() {
    	sessionFactory.close();
    }
 
    public void create(Method method) {  
        Session session = sessionFactory.openSession();
        
        List<Method> methods = new ArrayList<Method>();
        try {
			Query query = session.createQuery("from Method where name = '" + method.getName() + "' AND argumenttypes = '" + method.getArgumentTypes() + "' AND jar ='" + method.getClassId() + "'");
			methods = query.list();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
        
        if(!methods.isEmpty())
        	return;
        
        session.beginTransaction();
        session.save(method);
        session.getTransaction().commit();
        session.close();
    }
 
    protected Method read(long id) {
    	Session session = sessionFactory.openSession();
    	Method method = (Method) session.get(Method.class, id);     
        session.close();
        return method;
    }
    
    public List<Method> readAll() {
    	Session session = sessionFactory.openSession();
    	List<Method> methods = new ArrayList<Method>();
		try {
			Query query = session.createQuery("from Method");
			methods = query.list();
		} catch (RuntimeException e) {
			e.printStackTrace();
		} 
		return methods;
	}

    protected void update(Method method) {     
        Session session = sessionFactory.openSession();
        session.beginTransaction();
        session.update(method);
        session.getTransaction().commit();
        session.close();
    }
 
    protected void delete(Method method) {
        Session session = sessionFactory.openSession();
        session.beginTransaction();
        session.delete(method);
        session.getTransaction().commit();
        session.close();
    }
}
