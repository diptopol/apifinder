package ca.concordia.jaranalyzer.dao;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import ca.concordia.jaranalyzer.ClassInfo;
import ca.concordia.jaranalyzer.JarInfo;
import ca.concordia.jaranalyzer.MethodInfo;
import ca.concordia.jaranalyzer.PackageInfo;
import ca.concordia.jaranalyzer.model.Jar;
import ca.concordia.jaranalyzer.model.Package;
import ca.concordia.jaranalyzer.model.Class;
import ca.concordia.jaranalyzer.model.Method;
import ca.concordia.jaranalyzer.util.HibernateUtil;

public class JarManager {
	protected SessionFactory sessionFactory;

	public JarManager() {
		this.sessionFactory = HibernateUtil.getSessionFactory();
	}

	public void exit() {
		sessionFactory.close();
	}

	public Jar create(JarInfo jarInfo) {
		Jar jar = new Jar(jarInfo);
		if (!exists(jar.getName())) {
			Session session = sessionFactory.openSession();
			session.beginTransaction();
			session.save(jar);
			for (PackageInfo packageInfo: jarInfo.getPackages()) {
				Package pack = new Package(packageInfo);
				pack.setJarId(jar.getId());
				session.save(pack);
				for (ClassInfo classInfo : packageInfo.getClasses()) {
					Class cl = new Class(classInfo);
					cl.setPackageId(pack.getId());
					session.save(cl);
					for (MethodInfo methodInfo : classInfo.getPublicMethods()) {
						Method method = new Method(methodInfo);
						method.setClassId(cl.getId());
						session.save(method);
					}
				}
			}
			session.getTransaction().commit();
			session.close();
		}
		return read(jarInfo.getName());
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
