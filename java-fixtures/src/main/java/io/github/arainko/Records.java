package io.github.arainko;

public class Records {
  public static record SourceTopelevel(int intField, String str, SourceLevel1 level1) {

  }

  public static record SourceLevel1(SourceLevel2 level2, String str) {

  }

  public static record SourceLevel2(int intField) {

  }

  public static record DestTopelevel(int intField, String str, DestLevel1 level1) {

  }

  public static record DestLevel1(DestLevel2 level2, String str) {

  }

  public static record DestLevel2(int intField) {

  }

  public static record GenericToplevel<A>(int intField, String str, A level1) {
    
  }
}
