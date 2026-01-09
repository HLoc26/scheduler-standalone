package application.utils;


import application.models.ESession;

public class EnumMapper {
    public static engine.v2.definitions.ESession toEngineSession (application.models.ESession applicationSession){
        if(applicationSession == null) return null;
        return switch (applicationSession){
            case ESession.MORNING -> engine.v2.definitions.ESession.MORNING;
            case ESession.AFTERNOON -> engine.v2.definitions.ESession.AFTERNOON;
        };
    }
}
