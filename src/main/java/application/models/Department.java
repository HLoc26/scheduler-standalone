package application.models;

import java.util.ArrayList;
import java.util.List;

public class Department {
    private String id;
    private String name;
    private List<Subject> qualifiedSubjects;

    public Department(String id, String name) {
        this.id = id;
        this.name = name;
        this.qualifiedSubjects = new ArrayList<>();
    }

    public Department(String id, String name, List<Subject> qualifiedSubjects) {
        this.id = id;
        this.name = name;
        this.qualifiedSubjects = qualifiedSubjects;
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

    public List<Subject> getQualifiedSubjects() {
        return qualifiedSubjects;
    }

    public void setQualifiedSubjects(List<Subject> qualifiedSubjects) {
        this.qualifiedSubjects = qualifiedSubjects;
    }

    public void addQualifiedSubject(Subject subject) {
        if (!this.qualifiedSubjects.contains(subject)) {
            this.qualifiedSubjects.add(subject);
        }
    }

    public void removeQualifiedSubject(Subject subject) {
        this.qualifiedSubjects.remove(subject);
    }

    @Override
    public String toString() {
        return name;
    }
}
