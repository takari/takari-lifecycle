package record.reference;

class NestedParameterizedTypeSelfReference {
  static class Nested<S> {}

  <S> Nested<S> method() {
    return null;
  }
}
