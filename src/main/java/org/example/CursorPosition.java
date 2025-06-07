package org.example;

public class CursorPosition {
    public String userId;
    public int x;
    public int y;

    public CursorPosition() {}          // dla GSON-a
    public CursorPosition(String id,int x,int y){
        this.userId=id; this.x=x; this.y=y;
    }
}
