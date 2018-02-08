package ca.concordia.jaranalyzer.dao;

import ca.concordia.jaranalyzer.model.Method;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.Query;

import java.util.ArrayList;
import java.util.List;

public class MethodManager {
	protected SessionFactory sessionFactory;
	 
	public MethodManager(){   
    	setup();
	}
	
    public void setup() {
    	final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
    	        .configure() // configures settings from hibernate.cfg.xml
    	        .build();
    	try {
    	    sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
    	} catch (Exception ex) {
    	    StandardServiceRegistryBuilder.destroy(registry);
    	}
    }
 
    public void exit() {
    	sessionFactory.close();
    }
 
    public void create(Method method) {  
        Session session = sessionFactory.openSession();
        
        List<Method> methods = new ArrayList<Method>();
//        try {
//			Query query = session.createQuery("from Method where name = '" + method.getName() + "' AND argumenttypes = '" + method.getArgumentTypes() + "' AND jar ='" + method.getJar() + "'");
//			methods = query.list();
//		} catch (RuntimeException e) {
//			e.printStackTrace();
//		}
        
        if(!methods.isEmpty())
        	return;
        
        session.beginTransaction();
        session.save(method);
        session.getTransaction().commit();
        session.close();
    }
 
    protected Method read(long id) {
    	Session session = sessionFactory.openSession();
    	Method method = session.get(Method.class, id);     
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
