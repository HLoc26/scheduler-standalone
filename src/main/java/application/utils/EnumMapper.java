package application.utils;

import application.models.ESession;

public class EnumMapper {
    public static scheduler.common.models.ESession toEngineSession (ESession applicationSession){
        if(applicationSession == null) return null;
        return switch (applicationSession){
            case ESession.MORNING -> scheduler.common.models.ESession.MORNING;
            case ESession.AFTERNOON -> scheduler.common.models.ESession.AFTERNOON;
        };
    }
}
