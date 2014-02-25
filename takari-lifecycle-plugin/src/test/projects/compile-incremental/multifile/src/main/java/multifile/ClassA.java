package multifile;

public class ClassA
{
    public String getName()
    {
        return new ClassB.Nested().toString();
    }
}
