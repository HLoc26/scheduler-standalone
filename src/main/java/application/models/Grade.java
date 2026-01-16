package application.models;

import java.util.ArrayList;
import java.util.List;

public class Grade {
    private String id;
    private String name;
    private int level;
    private Session session;
    private List<Clazz> classes;

    public Grade(String id, String name, int level, Session session, List<Clazz> classes) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.session = session;
        this.classes = classes;
    }

    public Grade(String id, String name, int level, Session session) {
        this(id, name, level, session, new ArrayList<>());
    }

    public Grade(String id, String name, int level) {
        this(id, name, level, null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public List<Clazz> getClasses() {
        return classes;
    }

    public void setClasses(List<Clazz> classes) {
        this.classes = classes;
    }
}
