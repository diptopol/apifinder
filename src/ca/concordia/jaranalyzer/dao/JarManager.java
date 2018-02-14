package ca.concordia.jaranalyzer.dao;

import ca.concordia.jaranalyzer.model.Jar;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.Query;

import java.util.ArrayList;
import java.util.List;

public class JarManager {
	protected SessionFactory sessionFactory;

	public JarManager() {
		setup();
	}

	public void setup() {
//		final StandardServiceRegistry registry = new StandardServiceRegistryBuilder().configure().build(); // configures
																									// settings
																									// from
																									// hibernate.cfg.xml
				
		try {
//			Configuration configuration = new Configuration().configure();
//            ServiceRegistry serviceRegistry
//                = new StandardServiceRegistryBuilder()
//                    .applySettings(configuration.getProperties()).build();
            // builds a session factory from the service registry
//            SessionFactory sessionFactory = configuration.buildSessionFactory(serviceRegistry);

			sessionFactory = new Configuration().configure("hibernate.cfg.xml").buildSessionFactory();
//			sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
			
//			MetadataSources sources = new MetadataSources(registry);
//
//	        // Create Metadata
//	        Metadata metadata = sources.getMetadataBuilder().build();
//
//	        // Create SessionFactory
//	        sessionFactory = metadata.getSessionFactoryBuilder().build();
		} catch (Exception ex) {
			System.out.println(ex);
//			StandardServiceRegistryBuilder.destroy(registry);
		}
	}

	public void exit() {
		sessionFactory.close();
	}

	public Jar create(Jar jar) {
		if (!exists(jar.getName())) {
			Session session = sessionFactory.openSession();
			session.beginTransaction();
			session.save(jar);
			session.getTransaction().commit();
			session.close();
		}
		return read(jar.getName());
	}

	public boolean exists(String name) {
		Session session = sessionFactory.openSession();
		List<Jar> jars = new ArrayList<Jar>();
		try {
			Query query = session.createQuery("from Jar where name = '" + name + "'");
			jars = query.list();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		session.close();
		if (jars.isEmpty()) {
			return false;
		} else {
			return true;
		}
	}

	protected Jar read(String name) {
		List<Jar> jars = new ArrayList<Jar>();
		try {
			Session session = sessionFactory.openSession();
			Query query = session.createQuery("from Jar where name = '" + name + "'");
			jars = query.list();
			session.close();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		if (jars.isEmpty()) {
			return null;
		} else {
			return jars.get(0);
		}
	}

	protected Jar read(long id) {
		Session session = sessionFactory.openSession();
		Jar jar = (Jar) session.get(Jar.class, id);
		session.close();
		return jar;
	}

	public List<Jar> readAll() {
		Session session = sessionFactory.openSession();
		List<Jar> jars = new ArrayList<Jar>();
		try {
			Query query = session.createQuery("from Jar");
			jars = query.list();
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		return jars;
	}

	protected void update(Jar jar) {
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		session.update(jar);
		session.getTransaction().commit();
		session.close();
	}

	protected void delete(Jar jar) {
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		session.delete(jar);
		session.getTransaction().commit();
		session.close();
	}
}
