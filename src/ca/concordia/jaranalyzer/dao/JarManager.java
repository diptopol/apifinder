package ca.concordia.jaranalyzer.dao;

import ca.concordia.jaranalyzer.model.Jar;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.Query;

import java.util.ArrayList;
import java.util.List;

public class JarManager {
	protected SessionFactory sessionFactory;

	public JarManager() {
		setup();
	}

	public void setup() {
		final StandardServiceRegistry registry = new StandardServiceRegistryBuilder().configure() // configures
																									// settings
																									// from
																									// hibernate.cfg.xml
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
		Jar jar = session.get(Jar.class, id);
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
