package application.models;

import java.util.ArrayList;
import java.util.List;

public class Grade {
    private String id;
    private String name;
    private int level;
    private ESession session;
    private List<Clazz> classes;

    public Grade(String id, String name, int level, ESession session, List<Clazz> classes) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.session = session;
        this.classes = classes;
    }

    public Grade(String id, String name, int level, ESession session) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.session = session;
        this.classes = new ArrayList<>();
    }
    
    public Grade(String id, String name, int level) {
        this(id, name, level, ESession.MORNING);
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

    public ESession getSession() {
        return session;
    }

    public void setSession(ESession session) {
        this.session = session;
    }

    public List<Clazz> getClasses() {
        return classes;
    }

    public void setClasses(List<Clazz> classes) {
        this.classes = classes;
    }
}
